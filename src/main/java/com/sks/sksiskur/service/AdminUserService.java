package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.AdminRole;
import com.sks.sksiskur.domain.AdminUser;
import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.AdminUserRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.web.dto.AdminUserCreateRequest;
import com.sks.sksiskur.web.dto.AdminUserResponse;
import com.sks.sksiskur.web.dto.AdminUserUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {
    private final AdminUserRepository repository;
    private final BasvuruRepository basvuruRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAssignmentService adminAssignmentService;
    private final AuditLogService auditLogService;

    public AdminUserService(
            AdminUserRepository repository,
            BasvuruRepository basvuruRepository,
            PasswordEncoder passwordEncoder,
            AdminAssignmentService adminAssignmentService,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.basvuruRepository = basvuruRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAssignmentService = adminAssignmentService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(Long actorId) {
        return repository.findAllByOrderByAdSoyadAscUsernameAsc().stream()
                .map(user -> toResponse(user, actorId))
                .toList();
    }

    @Transactional
    public AdminUserResponse create(AdminUserCreateRequest request, String actorUsername) {
        AdminUser actor = requireSuperAdmin(actorUsername);
        String username = request.username().trim();
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu yönetici kullanıcı adı zaten kayıtlı.");
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAdSoyad(request.adSoyad().trim());
        user.setAktif(true);
        user.setRol(AdminRole.YONETICI);
        AdminUser saved = repository.save(user);
        auditLogService.log(Role.ADMIN, actor.getUsername(), actor.getAdSoyad(), IslemTuru.YONETICI_OLUSTUR, "YONETICI", saved.getId(),
                "Yönetici hesabı oluşturuldu: " + saved.getUsername(), saved.getAdSoyad());
        return toResponse(saved, actor.getId());
    }

    @Transactional
    public AdminUserResponse update(Long id, AdminUserUpdateRequest request, String actorUsername) {
        AdminUser actor = requireSuperAdmin(actorUsername);
        AdminUser user = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Yönetici hesabı bulunamadı."));
        boolean self = actor.getId().equals(id);
        String username = request.username().trim();
        if (repository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu yönetici kullanıcı adı zaten kayıtlı.");
        }
        if (self && Boolean.FALSE.equals(request.aktif())) {
            throw new ApiException(HttpStatus.CONFLICT, "Kendi hesabınızı pasifleştiremezsiniz.");
        }
        if (self && request.password() != null && !request.password().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "Kendi şifrenizi bu ekrandan değiştiremezsiniz.");
        }
        boolean deactivating = !request.aktif() && user.isAktif();
        if (deactivating && repository.findByAktifTrueOrderByIdAsc().size() == 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Son aktif yönetici pasifleştirilemez.");
        }
        if (deactivating && user.getRol() == AdminRole.SUPER_ADMIN
                && repository.countByRolAndAktifTrue(AdminRole.SUPER_ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Son aktif ana yönetici pasifleştirilemez.");
        }
        if (request.rol() != null && request.rol() != user.getRol()) {
            if (self) {
                throw new ApiException(HttpStatus.CONFLICT, "Kendi rolünüzü değiştiremezsiniz.");
            }
            if (user.getRol() == AdminRole.SUPER_ADMIN
                    && repository.countByRolAndAktifTrue(AdminRole.SUPER_ADMIN) <= 1) {
                throw new ApiException(HttpStatus.CONFLICT, "Son aktif ana yöneticinin rolü değiştirilemez.");
            }
            user.setRol(request.rol());
        }
        user.setUsername(username);
        user.setAdSoyad(request.adSoyad().trim());
        user.setAktif(request.aktif());
        if (!self && request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 6 || request.password().length() > 80) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Şifre en az 6 karakter olmalıdır.");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        AdminUser saved = repository.save(user);
        if (deactivating) {
            adminAssignmentService.reassignPendingFrom(saved);
        }
        String detail = saved.isAktif() ? "aktif" : "pasif";
        detail += ", " + saved.getRol().name();
        auditLogService.log(Role.ADMIN, actor.getUsername(), actor.getAdSoyad(), IslemTuru.YONETICI_GUNCELLE, "YONETICI", saved.getId(),
                "Yönetici hesabı güncellendi: " + saved.getUsername(), detail);
        return toResponse(saved, actor.getId());
    }

    private AdminUser requireSuperAdmin(String actorUsername) {
        AdminUser actor = repository.findByUsername(actorUsername)
                .filter(AdminUser::isAktif)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Yönetici hesabı bulunamadı."));
        if (actor.getRol() != AdminRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca ana yönetici tarafından yapılabilir.");
        }
        return actor;
    }

    private AdminUserResponse toResponse(AdminUser user, Long actorId) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getAdSoyad(),
                user.isAktif(),
                user.getRol(),
                basvuruRepository.countByAtananAdminIdAndStatus(user.getId(), ApplicationStatus.SUBMITTED),
                user.getOlusturmaTarihi(),
                actorId != null && actorId.equals(user.getId())
        );
    }
}
