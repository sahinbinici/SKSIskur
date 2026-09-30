package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.OgrenciPanosuDuyuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.OgrenciPanosuDuyuruRepository;
import com.sks.sksiskur.web.dto.YoneticiPanosuDuyuruKaydetRequest;
import com.sks.sksiskur.web.dto.YoneticiPanosuDuyuruResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OgrenciPanosuDuyuruService {

    private final OgrenciPanosuDuyuruRepository repository;
    private final AuditLogService auditLogService;

    public OgrenciPanosuDuyuruService(OgrenciPanosuDuyuruRepository repository, AuditLogService auditLogService) {
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
        OgrenciPanosuDuyuru duyuru = new OgrenciPanosuDuyuru();
        duyuru.setBaslik(request.baslik().trim());
        duyuru.setMesaj(request.mesaj().trim());
        duyuru.setAktif(request.aktif());
        duyuru.setGonderenAdmin(adminUsername);
        duyuru = repository.save(duyuru);
        log(adminUsername, duyuru, "Öğrenci portal duyurusu oluşturuldu");
        return toResponse(duyuru);
    }

    @Transactional
    public YoneticiPanosuDuyuruResponse update(Long id, String adminUsername, YoneticiPanosuDuyuruKaydetRequest request) {
        OgrenciPanosuDuyuru duyuru = require(id);
        duyuru.setBaslik(request.baslik().trim());
        duyuru.setMesaj(request.mesaj().trim());
        duyuru.setAktif(request.aktif());
        duyuru = repository.save(duyuru);
        log(adminUsername, duyuru, "Öğrenci portal duyurusu güncellendi");
        return toResponse(duyuru);
    }

    @Transactional
    public void delete(Long id, String adminUsername) {
        OgrenciPanosuDuyuru duyuru = require(id);
        repository.delete(duyuru);
        auditLogService.log(
                Role.ADMIN,
                adminUsername,
                null,
                IslemTuru.OGRENCI_PANO_DUYURU,
                "OGRENCI_PANO_DUYURU",
                id,
                "Öğrenci portal duyurusu silindi: " + duyuru.getBaslik(),
                null
        );
    }

    private OgrenciPanosuDuyuru require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Duyuru bulunamadı."));
    }

    private void log(String adminUsername, OgrenciPanosuDuyuru duyuru, String aciklama) {
        auditLogService.log(
                Role.ADMIN,
                adminUsername,
                null,
                IslemTuru.OGRENCI_PANO_DUYURU,
                "OGRENCI_PANO_DUYURU",
                duyuru.getId(),
                aciklama + ": " + duyuru.getBaslik(),
                duyuru.isAktif() ? "Aktif" : "Pasif"
        );
    }

    private YoneticiPanosuDuyuruResponse toResponse(OgrenciPanosuDuyuru duyuru) {
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
