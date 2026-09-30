package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.KayitListesi;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.KayitListesiRepository;
import com.sks.sksiskur.web.dto.ImzaBildirimiGonderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ImzaBildirimiService {

    private final BasvuruRepository basvuruRepository;
    private final KayitListesiRepository kayitListesiRepository;
    private final KampusResolver kampusResolver;
    private final EmailNotificationService emailNotificationService;
    private final BasvuruDonemiService basvuruDonemiService;
    private final AuditLogService auditLogService;

    public ImzaBildirimiService(
            BasvuruRepository basvuruRepository,
            KayitListesiRepository kayitListesiRepository,
            KampusResolver kampusResolver,
            EmailNotificationService emailNotificationService,
            BasvuruDonemiService basvuruDonemiService,
            AuditLogService auditLogService
    ) {
        this.basvuruRepository = basvuruRepository;
        this.kayitListesiRepository = kayitListesiRepository;
        this.kampusResolver = kampusResolver;
        this.emailNotificationService = emailNotificationService;
        this.basvuruDonemiService = basvuruDonemiService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public boolean imzaBildirimiGonderildi(BasvuruDonemi donem) {
        return kayitListesiRepository.findByBasvuruDonemiId(donem.getId())
                .map(KayitListesi::isImzaBildirimiGonderildi)
                .orElse(false);
    }

    @Transactional
    public ImzaBildirimiGonderResponse gonder(String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        KayitListesi liste = kayitListesiRepository.findByBasvuruDonemiId(donem.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Kesin liste kaydı bulunamadı."));
        if (!liste.isKesinOnaylandi()) {
            throw new ApiException(HttpStatus.CONFLICT, "İmza bildirimi göndermeden önce kesin liste karşılaştırması onaylanmalıdır.");
        }
        long atanan = basvuruRepository.countByStatusAndAtananBirimKoduIsNotNullAndBasvuruDonemiId(
                ApplicationStatus.APPROVED, donem.getId());
        if (atanan > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Birim dağıtımı yapıldıktan sonra imza bildirimi gönderilemez.");
        }
        if (liste.isImzaBildirimiGonderildi()) {
            throw new ApiException(HttpStatus.CONFLICT, "İmza bildirimi zaten gönderildi.");
        }

        List<Basvuru> hedefler = basvuruRepository
                .findByStatusAndKayitTuruAndKesinListedeTrueAndAtananBirimKoduIsNullAndBasvuruDonemiId(
                        ApplicationStatus.APPROVED, KayitTuru.KESIN, donem.getId());
        if (hedefler.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "İmza bildirimi gönderilecek kesin kayıtlı öğrenci bulunamadı.");
        }

        Instant now = Instant.now();
        int epostaGonderilen = 0;
        int epostaAtlanan = 0;
        int epostaBasarisiz = 0;
        String emailSubject = kampusResolver.buildEmailSubject();

        for (Basvuru basvuru : hedefler) {
            String sayfaMesaji = kampusResolver.buildImzaMesaji(basvuru.getStudent());
            basvuru.setImzaBildirimiMesaji(sayfaMesaji);
            basvuru.setImzaBildirimiGonderildi(true);
            basvuru.setImzaBildirimiGonderimTarihi(now);
            basvuru.setImzaBildirimiOkundu(false);

            String eposta = basvuru.getStudent().getEposta();
            if (eposta == null || eposta.isBlank()) {
                basvuru.setImzaBildirimiEpostaGonderildi(false);
                epostaAtlanan++;
                continue;
            }
            if (!emailNotificationService.isEnabled()) {
                basvuru.setImzaBildirimiEpostaGonderildi(false);
                epostaAtlanan++;
                continue;
            }
            boolean sent = emailNotificationService.sendPlainText(eposta, emailSubject, sayfaMesaji);
            basvuru.setImzaBildirimiEpostaGonderildi(sent);
            if (sent) {
                epostaGonderilen++;
            } else {
                epostaBasarisiz++;
            }
        }

        basvuruRepository.saveAll(hedefler);
        liste.setImzaBildirimiGonderildi(true);
        liste.setImzaBildirimiGonderimTarihi(now);
        liste.setImzaBildirimiGonderenAdmin(adminUsername);
        kayitListesiRepository.save(liste);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.IMZA_BILDIRIMI, "DONEM", donem.getId(),
                "İmza bildirimi gönderildi: " + donem.getAd(), hedefler.size() + " öğrenci");

        return new ImzaBildirimiGonderResponse(
                hedefler.size(),
                hedefler.size(),
                epostaGonderilen,
                epostaAtlanan,
                epostaBasarisiz,
                now,
                adminUsername
        );
    }

    @Transactional
    public void resetForDonem(BasvuruDonemi donem) {
        kayitListesiRepository.findByBasvuruDonemiId(donem.getId()).ifPresent(liste -> {
            liste.setImzaBildirimiGonderildi(false);
            liste.setImzaBildirimiGonderimTarihi(null);
            liste.setImzaBildirimiGonderenAdmin(null);
            kayitListesiRepository.save(liste);
        });
        List<Basvuru> basvurular = basvuruRepository.findByStatusAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
        for (Basvuru basvuru : basvurular) {
            basvuru.setImzaBildirimiMesaji(null);
            basvuru.setImzaBildirimiGonderildi(false);
            basvuru.setImzaBildirimiGonderimTarihi(null);
            basvuru.setImzaBildirimiOkundu(false);
            basvuru.setImzaBildirimiEpostaGonderildi(false);
        }
        basvuruRepository.saveAll(basvurular);
    }
}
