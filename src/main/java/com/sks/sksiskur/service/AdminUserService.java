package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.AdminUser;
import com.sks.sksiskur.domain.ApplicationStatus;
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

    public AdminUserService(AdminUserRepository repository, BasvuruRepository basvuruRepository, PasswordEncoder passwordEncoder,
                            AdminAssignmentService adminAssignmentService) {
        this.repository = repository;
        this.basvuruRepository = basvuruRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAssignmentService = adminAssignmentService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return repository.findAllByOrderByAdSoyadAscUsernameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminUserResponse create(AdminUserCreateRequest request) {
        String username = request.username().trim();
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu yönetici kullanıcı adı zaten kayıtlı.");
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAdSoyad(request.adSoyad().trim());
        user.setAktif(true);
        return toResponse(repository.save(user));
    }

    @Transactional
    public AdminUserResponse update(Long id, AdminUserUpdateRequest request) {
        AdminUser user = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Yönetici hesabı bulunamadı."));
        String username = request.username().trim();
        if (repository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu yönetici kullanıcı adı zaten kayıtlı.");
        }
        boolean deactivating = !request.aktif() && user.isAktif();
        if (deactivating && repository.findByAktifTrueOrderByIdAsc().size() == 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Son aktif yönetici pasifleştirilemez.");
        }
        user.setUsername(username);
        user.setAdSoyad(request.adSoyad().trim());
        user.setAktif(request.aktif());
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 6 || request.password().length() > 80) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Şifre en az 6 karakter olmalıdır.");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        AdminUser saved = repository.save(user);
        if (deactivating) {
            adminAssignmentService.reassignPendingFrom(saved);
        }
        return toResponse(saved);
    }

    private AdminUserResponse toResponse(AdminUser user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getAdSoyad(), user.isAktif(),
                basvuruRepository.countByAtananAdminIdAndStatus(user.getId(), ApplicationStatus.SUBMITTED),
                user.getOlusturmaTarihi());
    }
}
