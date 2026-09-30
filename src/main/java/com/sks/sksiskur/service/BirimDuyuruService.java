package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BirimDuyuru;
import com.sks.sksiskur.domain.BirimDuyuruOkuma;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BirimDuyuruOkumaRepository;
import com.sks.sksiskur.repository.BirimDuyuruRepository;
import com.sks.sksiskur.web.dto.BirimDuyuruGonderRequest;
import com.sks.sksiskur.web.dto.BirimDuyuruGonderResponse;
import com.sks.sksiskur.web.dto.BirimDuyuruInboxResponse;
import com.sks.sksiskur.web.dto.BirimDuyuruResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BirimDuyuruService {

    private final BirimDuyuruRepository duyuruRepository;
    private final BirimDuyuruOkumaRepository okumaRepository;
    private final DagitimBirimiService dagitimBirimiService;
    private final AuditLogService auditLogService;

    public BirimDuyuruService(
            BirimDuyuruRepository duyuruRepository,
            BirimDuyuruOkumaRepository okumaRepository,
            DagitimBirimiService dagitimBirimiService,
            AuditLogService auditLogService
    ) {
        this.duyuruRepository = duyuruRepository;
        this.okumaRepository = okumaRepository;
        this.dagitimBirimiService = dagitimBirimiService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BirimDuyuruResponse> listForAdmin() {
        return duyuruRepository.findAllByOrderByGonderimTarihiDesc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BirimDuyuruInboxResponse> listForBirim(String birimKodu) {
        return okumaRepository.findByBirimKoduOrderByDuyuru_GonderimTarihiDesc(birimKodu).stream()
                .map(this::toInboxResponse)
                .toList();
    }

    @Transactional
    public BirimDuyuruGonderResponse send(String adminUsername, BirimDuyuruGonderRequest request) {
        Set<String> targets = resolveTargets(request);
        if (targets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duyuru gönderilecek birim bulunamadı.");
        }

        BirimDuyuru duyuru = new BirimDuyuru();
        duyuru.setBaslik(request.baslik().trim());
        duyuru.setMesaj(request.mesaj().trim());
        duyuru.setGonderenAdmin(adminUsername);
        duyuru.setTumBirimler(request.tumBirimler());
        duyuru = duyuruRepository.save(duyuru);

        for (String birimKodu : targets) {
            BirimDuyuruOkuma okuma = new BirimDuyuruOkuma();
            okuma.setDuyuru(duyuru);
            okuma.setBirimKodu(birimKodu);
            okuma.setOkundu(false);
            okumaRepository.save(okuma);
        }

        auditLogService.log(
                com.sks.sksiskur.domain.Role.ADMIN,
                adminUsername,
                null,
                IslemTuru.BIRIM_DUYURU_GONDER,
                "BIRIM_DUYURU",
                duyuru.getId(),
                "Birim duyurusu gönderildi: " + duyuru.getBaslik(),
                request.tumBirimler() ? "Tüm birimler (" + targets.size() + ")" : targets.size() + " birim"
        );

        return new BirimDuyuruGonderResponse(duyuru.getId(), targets.size());
    }

    @Transactional
    public BirimDuyuruInboxResponse markRead(String birimKodu, Long duyuruId) {
        BirimDuyuruOkuma okuma = okumaRepository.findByDuyuruIdAndBirimKodu(duyuruId, birimKodu)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Duyuru bulunamadı."));
        if (!okuma.isOkundu()) {
            okuma.setOkundu(true);
            okuma.setOkumaTarihi(Instant.now());
            okumaRepository.save(okuma);
        }
        return toInboxResponse(okuma);
    }

    private Set<String> resolveTargets(BirimDuyuruGonderRequest request) {
        Set<String> known = dagitimBirimiService.allUnits().stream()
                .map(unit -> unit.unit().kod())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (request.tumBirimler()) {
            return known;
        }
        if (request.birimKodlari() == null || request.birimKodlari().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "En az bir birim seçilmelidir.");
        }
        Set<String> targets = new LinkedHashSet<>();
        for (String kod : request.birimKodlari()) {
            String trimmed = kod == null ? "" : kod.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (!known.contains(trimmed)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Geçersiz birim kodu: " + trimmed);
            }
            targets.add(trimmed);
        }
        return targets;
    }

    private BirimDuyuruResponse toAdminResponse(BirimDuyuru duyuru) {
        List<String> birimKodlari = okumaRepository.findByDuyuruIdOrderByBirimKoduAsc(duyuru.getId()).stream()
                .map(BirimDuyuruOkuma::getBirimKodu)
                .toList();
        int hedefSayisi = birimKodlari.size();
        int okunanSayisi = (int) okumaRepository.countByDuyuruIdAndOkunduTrue(duyuru.getId());
        return new BirimDuyuruResponse(
                duyuru.getId(),
                duyuru.getBaslik(),
                duyuru.getMesaj(),
                duyuru.getGonderenAdmin(),
                duyuru.getGonderimTarihi(),
                duyuru.isTumBirimler(),
                hedefSayisi,
                okunanSayisi,
                birimKodlari
        );
    }

    private BirimDuyuruInboxResponse toInboxResponse(BirimDuyuruOkuma okuma) {
        BirimDuyuru duyuru = okuma.getDuyuru();
        return new BirimDuyuruInboxResponse(
                duyuru.getId(),
                duyuru.getBaslik(),
                duyuru.getMesaj(),
                duyuru.getGonderenAdmin(),
                duyuru.getGonderimTarihi(),
                okuma.isOkundu()
        );
    }
}
