package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDalga;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.web.dto.ImzaBildirimiGonderResponse;
import com.sks.sksiskur.web.dto.SozlesmeImzaBekleyenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class ImzaBildirimiService {

    private final BasvuruRepository basvuruRepository;
    private final BasvuruDalgaService basvuruDalgaService;
    private final KampusResolver kampusResolver;
    private final EmailNotificationService emailNotificationService;
    private final BasvuruDonemiService basvuruDonemiService;
    private final AuditLogService auditLogService;

    public ImzaBildirimiService(
            BasvuruRepository basvuruRepository,
            BasvuruDalgaService basvuruDalgaService,
            KampusResolver kampusResolver,
            EmailNotificationService emailNotificationService,
            BasvuruDonemiService basvuruDonemiService,
            AuditLogService auditLogService
    ) {
        this.basvuruRepository = basvuruRepository;
        this.basvuruDalgaService = basvuruDalgaService;
        this.kampusResolver = kampusResolver;
        this.emailNotificationService = emailNotificationService;
        this.basvuruDonemiService = basvuruDonemiService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public boolean imzaBildirimiGonderildi(BasvuruDonemi donem) {
        return basvuruDalgaService.requireAktifDalga(donem).isImzaBildirimiGonderildi();
    }

    @Transactional
    public ImzaBildirimiGonderResponse gonder(String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        BasvuruDalga dalga = basvuruDalgaService.requireAktifDalga(donem);
        if (!dalga.isKesinOnaylandi()) {
            throw new ApiException(HttpStatus.CONFLICT, "İmza bildirimi göndermeden önce İŞKUR nihai listesi yüklenmelidir.");
        }
        long atanan = basvuruRepository.countByStatusAndAtananBirimKoduIsNotNullAndBasvuruDonemiId(
                ApplicationStatus.APPROVED, donem.getId());
        if (atanan > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Birim dağıtımı yapıldıktan sonra imza bildirimi gönderilemez.");
        }
        if (dalga.isImzaBildirimiGonderildi()) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu tur için imza bildirimi zaten gönderildi.");
        }

        List<Basvuru> hedefler = basvuruRepository
                .findByStatusAndKayitTuruAndKesinListedeTrueAndAtananBirimKoduIsNullAndBasvuruDonemiId(
                        ApplicationStatus.APPROVED, KayitTuru.KESIN, donem.getId())
                .stream()
                .filter(b -> !b.isSozlesmeImzaPasif())
                .toList();
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

            String eposta = basvuru.resolveIletisimEposta();
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
        dalga.setImzaBildirimiGonderildi(true);
        dalga.setImzaBildirimiGonderimTarihi(now);
        dalga.setImzaBildirimiGonderenAdmin(adminUsername);
        basvuruDalgaService.saveDalga(dalga);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.IMZA_BILDIRIMI, "DONEM", donem.getId(),
                "İmza bildirimi gönderildi: " + dalga.getAd(), hedefler.size() + " öğrenci");

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

    @Transactional(readOnly = true)
    public List<SozlesmeImzaBekleyenResponse> listSozlesmeImzaBekleyen(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        if (!basvuruDalgaService.requireAktifDalga(donem).isImzaBildirimiGonderildi()) {
            return List.of();
        }
        return basvuruRepository
                .findByStatusAndKayitTuruAndKesinListedeTrueAndAtananBirimKoduIsNullAndBasvuruDonemiId(
                        ApplicationStatus.APPROVED, KayitTuru.KESIN, donem.getId())
                .stream()
                .filter(b -> b.isImzaBildirimiGonderildi())
                .filter(b -> !b.isSozlesmeImzalandi())
                .filter(b -> !b.isSozlesmeImzaPasif())
                .sorted(Comparator.comparing((Basvuru b) -> b.getStudent().getSoyad(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(b -> b.getStudent().getAd(), String.CASE_INSENSITIVE_ORDER))
                .map(b -> new SozlesmeImzaBekleyenResponse(
                        b.getId(),
                        b.getStudent().getOgrenciNo(),
                        b.getStudent().getTcKimlikNo(),
                        b.getStudent().getAdSoyad(),
                        b.getStudent().getFakulte(),
                        b.getStudent().getProgram(),
                        b.isImzaBildirimiOkundu(),
                        b.isImzaBildirimiEpostaGonderildi(),
                        b.getImzaBildirimiGonderimTarihi()
                ))
                .toList();
    }

    @Transactional
    public void markSozlesmeImzalandi(Long basvuruId, String adminUsername) {
        Basvuru basvuru = requireImzaBekleyen(basvuruId);
        basvuru.setSozlesmeImzalandi(true);
        basvuru.setSozlesmeImzaTarihi(LocalDate.now());
        basvuruRepository.save(basvuru);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.SOZLESME_IMZA_LANDI, "BASVURU", basvuruId,
                "Sözleşme imzası işlendi: " + basvuru.getStudent().getOgrenciNo(), null);
    }

    @Transactional
    public int markSozlesmeImzaPasif(List<Long> basvuruIds, String adminUsername) {
        if (basvuruIds == null || basvuruIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Long basvuruId : basvuruIds) {
            Basvuru basvuru = requireImzaBekleyen(basvuruId);
            basvuru.setSozlesmeImzaPasif(true);
            basvuru.setSozlesmeImzaPasifTarihi(now);
            basvuru.setSozlesmeImzaPasifAdmin(adminUsername);
            basvuru.setIliskiBitisTarihi(today);
            basvuru.setKayitTuru(null);
            basvuru.setKesinListede(false);
            basvuru.setKayitTarihi(null);
            basvuruRepository.save(basvuru);
            auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.SOZLESME_IMZA_PASIF, "BASVURU",
                    basvuruId, "Sözleşme imzası gelmedi — pasif: " + basvuru.getStudent().getOgrenciNo(), null);
            count++;
        }
        return count;
    }

    private Basvuru requireImzaBekleyen(Long basvuruId) {
        Basvuru basvuru = basvuruRepository.findDetailedById(basvuruId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı."));
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        if (basvuru.getBasvuruDonemi() == null || !basvuru.getBasvuruDonemi().getId().equals(donem.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca aktif dönemdeki kayıtlar işlenebilir.");
        }
        if (basvuru.getStatus() != ApplicationStatus.APPROVED || basvuru.getKayitTuru() != KayitTuru.KESIN) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca kesin kayıtlı onaylı başvurular işlenebilir.");
        }
        if (basvuru.isAssigned()) {
            throw new ApiException(HttpStatus.CONFLICT, "Birime atanmış öğrenci için bu işlem yapılamaz.");
        }
        if (!basvuru.isImzaBildirimiGonderildi()) {
            throw new ApiException(HttpStatus.CONFLICT, "İmza bildirimi gönderilmemiş öğrenci işlenemez.");
        }
        if (basvuru.isSozlesmeImzalandi()) {
            throw new ApiException(HttpStatus.CONFLICT, "Sözleşme imzası zaten işlenmiş.");
        }
        if (basvuru.isSozlesmeImzaPasif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Öğrenci zaten pasife alınmış.");
        }
        return basvuru;
    }

    @Transactional
    public void resetForActiveDalga(BasvuruDonemi donem) {
        BasvuruDalga dalga = basvuruDalgaService.requireAktifDalga(donem);
        dalga.setImzaBildirimiGonderildi(false);
        dalga.setImzaBildirimiGonderimTarihi(null);
        dalga.setImzaBildirimiGonderenAdmin(null);
        basvuruDalgaService.saveDalga(dalga);
        List<Basvuru> basvurular = basvuruRepository.findByStatusAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
        for (Basvuru basvuru : basvurular) {
            if (basvuru.isAssigned() && basvuru.getKayitTuru() == KayitTuru.KESIN) {
                continue;
            }
            basvuru.setImzaBildirimiMesaji(null);
            basvuru.setImzaBildirimiGonderildi(false);
            basvuru.setImzaBildirimiGonderimTarihi(null);
            basvuru.setImzaBildirimiOkundu(false);
            basvuru.setImzaBildirimiEpostaGonderildi(false);
            basvuru.setSozlesmeImzalandi(false);
            basvuru.setSozlesmeImzaTarihi(null);
            basvuru.setSozlesmeImzaPasif(false);
            basvuru.setSozlesmeImzaPasifTarihi(null);
            basvuru.setSozlesmeImzaPasifAdmin(null);
        }
        basvuruRepository.saveAll(basvurular);
    }
}
