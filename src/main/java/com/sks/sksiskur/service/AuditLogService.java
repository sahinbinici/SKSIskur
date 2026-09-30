package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.IslemLog;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.repository.IslemLogRepository;
import com.sks.sksiskur.security.AuthPrincipal;
import com.sks.sksiskur.web.dto.IslemLogPageResponse;
import com.sks.sksiskur.web.dto.IslemLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AuditLogService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final IslemLogRepository repository;

    public AuditLogService(IslemLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            Role rol,
            String kullaniciAdi,
            String adSoyad,
            IslemTuru tur,
            String varlikTipi,
            Long varlikId,
            String aciklama,
            String detay
    ) {
        if (kullaniciAdi == null || kullaniciAdi.isBlank() || aciklama == null || aciklama.isBlank()) {
            return;
        }
        IslemLog entry = new IslemLog();
        entry.setRol(rol);
        entry.setKullaniciAdi(kullaniciAdi.trim());
        entry.setAdSoyad(trimOrNull(adSoyad));
        entry.setTur(tur);
        entry.setVarlikTipi(trimOrNull(varlikTipi));
        entry.setVarlikId(varlikId);
        entry.setAciklama(aciklama.trim());
        entry.setDetay(trimDetail(detay));
        repository.save(entry);
    }

    public void logAdmin(Authentication authentication, IslemTuru tur, String varlikTipi, Long varlikId, String aciklama, String detay) {
        AuthPrincipal principal = principal(authentication);
        if (principal == null) {
            return;
        }
        log(Role.ADMIN, principal.getUsername(), principal.getUsername(), tur, varlikTipi, varlikId, aciklama, detay);
    }

    public void logBirim(Authentication authentication, String birimKodu, IslemTuru tur, String varlikTipi, Long varlikId, String aciklama, String detay) {
        AuthPrincipal principal = principal(authentication);
        if (principal == null) {
            return;
        }
        String label = birimKodu != null && !birimKodu.isBlank() ? birimKodu : principal.getUsername();
        log(Role.BIRIM, principal.getUsername(), label, tur, varlikTipi, varlikId, aciklama, detay);
    }

    @Transactional(readOnly = true)
    public IslemLogPageResponse list(
            IslemTuru tur,
            Role rol,
            String kullanici,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZONE).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1);
        String query = kullanici == null || kullanici.isBlank() ? null : kullanici.trim();
        Page<IslemLog> result = repository.search(tur, rol, query, fromInstant, toInstant, pageable);
        return new IslemLogPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private IslemLogResponse toResponse(IslemLog log) {
        return new IslemLogResponse(
                log.getId(),
                log.getZaman(),
                log.getRol(),
                log.getTur(),
                log.getKullaniciAdi(),
                log.getAdSoyad(),
                log.getVarlikTipi(),
                log.getVarlikId(),
                log.getAciklama(),
                log.getDetay()
        );
    }

    private AuthPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String trimDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
    }
}
