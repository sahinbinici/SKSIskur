package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.domain.YoneticiPanosuDuyuru;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.YoneticiPanosuDuyuruRepository;
import com.sks.sksiskur.web.dto.YoneticiPanosuDuyuruKaydetRequest;
import com.sks.sksiskur.web.dto.YoneticiPanosuDuyuruResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class YoneticiPanosuDuyuruService {

    private final YoneticiPanosuDuyuruRepository repository;
    private final AuditLogService auditLogService;

    public YoneticiPanosuDuyuruService(YoneticiPanosuDuyuruRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<YoneticiPanosuDuyuruResponse> listActive() {
        return repository.findByAktifTrueOrderByOlusturmaTarihiDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<YoneticiPanosuDuyuruResponse> listAll() {
        return repository.findAllByOrderByOlusturmaTarihiDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public YoneticiPanosuDuyuruResponse create(String adminUsername, YoneticiPanosuDuyuruKaydetRequest request) {
        YoneticiPanosuDuyuru duyuru = new YoneticiPanosuDuyuru();
        duyuru.setBaslik(request.baslik().trim());
        duyuru.setMesaj(request.mesaj().trim());
        duyuru.setAktif(request.aktif());
        duyuru.setGonderenAdmin(adminUsername);
        duyuru = repository.save(duyuru);
        log(adminUsername, duyuru, "Ana sayfa duyurusu oluşturuldu");
        return toResponse(duyuru);
    }

    @Transactional
    public YoneticiPanosuDuyuruResponse update(Long id, String adminUsername, YoneticiPanosuDuyuruKaydetRequest request) {
        YoneticiPanosuDuyuru duyuru = require(id);
        duyuru.setBaslik(request.baslik().trim());
        duyuru.setMesaj(request.mesaj().trim());
        duyuru.setAktif(request.aktif());
        duyuru = repository.save(duyuru);
        log(adminUsername, duyuru, "Ana sayfa duyurusu güncellendi");
        return toResponse(duyuru);
    }

    @Transactional
    public void delete(Long id, String adminUsername) {
        YoneticiPanosuDuyuru duyuru = require(id);
        repository.delete(duyuru);
        auditLogService.log(
                Role.ADMIN,
                adminUsername,
                null,
                IslemTuru.YONETICI_PANO_DUYURU,
                "YONETICI_PANO_DUYURU",
                id,
                "Ana sayfa duyurusu silindi: " + duyuru.getBaslik(),
                null
        );
    }

    private YoneticiPanosuDuyuru require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Duyuru bulunamadı."));
    }

    private void log(String adminUsername, YoneticiPanosuDuyuru duyuru, String aciklama) {
        auditLogService.log(
                Role.ADMIN,
                adminUsername,
                null,
                IslemTuru.YONETICI_PANO_DUYURU,
                "YONETICI_PANO_DUYURU",
                duyuru.getId(),
                aciklama + ": " + duyuru.getBaslik(),
                duyuru.isAktif() ? "Aktif" : "Pasif"
        );
    }

    private YoneticiPanosuDuyuruResponse toResponse(YoneticiPanosuDuyuru duyuru) {
        return new YoneticiPanosuDuyuruResponse(
                duyuru.getId(),
                duyuru.getBaslik(),
                duyuru.getMesaj(),
                duyuru.isAktif(),
                duyuru.getGonderenAdmin(),
                duyuru.getOlusturmaTarihi(),
                duyuru.getGuncellemeTarihi()
        );
    }
}
