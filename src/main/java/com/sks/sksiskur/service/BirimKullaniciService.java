package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BirimKullanici;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BirimKullaniciRepository;
import com.sks.sksiskur.sicil.WorkUnit;
import com.sks.sksiskur.web.dto.BirimKullaniciCreateRequest;
import com.sks.sksiskur.web.dto.BirimKullaniciResponse;
import com.sks.sksiskur.web.dto.BirimKullaniciUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BirimKullaniciService {

    private final BirimKullaniciRepository repository;
    private final DagitimBirimiService dagitimBirimiService;
    private final PasswordEncoder passwordEncoder;

    public BirimKullaniciService(
            BirimKullaniciRepository repository,
            DagitimBirimiService dagitimBirimiService,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.dagitimBirimiService = dagitimBirimiService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<BirimKullaniciResponse> list() {
        return repository.findAllByOrderByBirimAdiAscUsernameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BirimKullaniciResponse create(BirimKullaniciCreateRequest request) {
        String username = request.username().trim();
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu kullanıcı adı zaten kayıtlı.");
        }
        WorkUnit unit = requireUnit(request.birimKodu());
        BirimKullanici user = new BirimKullanici();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAdSoyad(request.adSoyad().trim());
        user.setBirimKodu(unit.kod());
        user.setBirimAdi(unit.displayName());
        user.setAktif(true);
        return toResponse(repository.save(user));
    }

    @Transactional
    public BirimKullaniciResponse update(Long id, BirimKullaniciUpdateRequest request) {
        BirimKullanici user = require(id);
        String username = request.username().trim();
        if (repository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu kullanıcı adı zaten kayıtlı.");
        }
        WorkUnit unit = requireUnit(request.birimKodu());
        user.setUsername(username);
        user.setAdSoyad(request.adSoyad().trim());
        user.setBirimKodu(unit.kod());
        user.setBirimAdi(unit.displayName());
        user.setAktif(request.aktif());
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 6 || request.password().length() > 80) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Şifre en az 6 karakter olmalıdır");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(repository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        BirimKullanici user = require(id);
        repository.delete(user);
    }

    private BirimKullanici require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Birim hesabı bulunamadı."));
    }

    private WorkUnit requireUnit(String birimKodu) {
        String kod = birimKodu.trim();
        return dagitimBirimiService.allUnits().stream()
                .map(DagitimBirimiService.ConfiguredUnit::unit)
                .filter(unit -> unit.kod().equals(kod))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Geçerli bir birim seçiniz."));
    }

    private BirimKullaniciResponse toResponse(BirimKullanici user) {
        return new BirimKullaniciResponse(
                user.getId(),
                user.getUsername(),
                user.getAdSoyad(),
                user.getBirimKodu(),
                user.getBirimAdi(),
                user.isAktif(),
                user.getOlusturmaTarihi()
        );
    }
}
