package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BasvuruDalga;
import com.sks.sksiskur.domain.BasvuruDalgaDurum;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruDalgaRepository;
import com.sks.sksiskur.repository.BasvuruDonemiRepository;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
import com.sks.sksiskur.repository.KesinKayitKaydiRepository;
import com.sks.sksiskur.web.dto.BasvuruDalgaCreateRequest;
import com.sks.sksiskur.web.dto.BasvuruDalgaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class BasvuruDalgaService {

    private final BasvuruDalgaRepository dalgaRepository;
    private final BasvuruDonemiRepository donemRepository;
    private final IskurBasvuruKaydiRepository iskurKayitRepository;
    private final KesinKayitKaydiRepository kesinKayitRepository;
    private final AuditLogService auditLogService;

    public BasvuruDalgaService(
            BasvuruDalgaRepository dalgaRepository,
            BasvuruDonemiRepository donemRepository,
            IskurBasvuruKaydiRepository iskurKayitRepository,
            KesinKayitKaydiRepository kesinKayitRepository,
            AuditLogService auditLogService
    ) {
        this.dalgaRepository = dalgaRepository;
        this.donemRepository = donemRepository;
        this.iskurKayitRepository = iskurKayitRepository;
        this.kesinKayitRepository = kesinKayitRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public BasvuruDalga ensureInitialDalga(BasvuruDonemi donem) {
        List<BasvuruDalga> existing = dalgaRepository.findByBasvuruDonemiIdOrderByTurNoAsc(donem.getId());
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        return createDalgaEntity(donem, 1, donem.getOgrenciBaslangicTarihi(), donem.getOgrenciBitisTarihi(), true);
    }

    @Transactional(readOnly = true)
    public List<BasvuruDalgaResponse> list(Long donemId) {
        BasvuruDonemi donem = requireDonem(donemId);
        if (dalgaRepository.countByBasvuruDonemiId(donem.getId()) == 0) {
            ensureInitialDalga(donem);
        }
        return dalgaRepository.findByBasvuruDonemiIdOrderByTurNoAsc(donem.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BasvuruDalga requireAktifDalga(BasvuruDonemi donem) {
        return dalgaRepository.findByBasvuruDonemiIdAndAktifTrue(donem.getId())
                .orElseGet(() -> {
                    if (dalgaRepository.countByBasvuruDonemiId(donem.getId()) == 0) {
                        return ensureInitialDalga(donem);
                    }
                    return ensureInitialDalgaReadOnly(donem);
                });
    }

    @Transactional(readOnly = true)
    public BasvuruDalga requireOpenDalgaForStudent(BasvuruDonemi donem) {
        BasvuruDalga dalga = requireAktifDalga(donem);
        if (dalga.getDurum() != BasvuruDalgaDurum.ACIK) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu dönemde yeni başvuru turu açık değil.");
        }
        if (!isStudentAccessOpen(dalga)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Öğrenci başvuru giriş süresi şu anda açık değil.");
        }
        return dalga;
    }

    @Transactional(readOnly = true)
    public boolean isStudentAccessOpen(BasvuruDonemi donem) {
        if (!donem.isAktif()) {
            return false;
        }
        return dalgaRepository.findByBasvuruDonemiIdAndAktifTrue(donem.getId())
                .map(this::isStudentAccessOpen)
                .orElse(isLegacyStudentAccessOpen(donem));
    }

    @Transactional
    public BasvuruDalgaResponse createNext(Long donemId, BasvuruDalgaCreateRequest request, String adminUsername) {
        BasvuruDonemi donem = requireDonem(donemId);
        if (!donem.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca aktif döneme yeni tur açılabilir.");
        }
        if (request.ogrenciBitisTarihi().isBefore(request.ogrenciBaslangicTarihi())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bitiş tarihi başlangıç tarihinden önce olamaz.");
        }
        BasvuruDalga current = dalgaRepository.findByBasvuruDonemiIdAndAktifTrue(donem.getId()).orElse(null);
        if (current != null) {
            if (!current.isKesinOnaylandi() || !current.isImzaBildirimiGonderildi()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Yeni tur için önce mevcut turda kesin liste onayı ve imza bildirimi tamamlanmalıdır.");
            }
            current.setDurum(BasvuruDalgaDurum.TAMAMLANDI);
            current.setAktif(false);
            dalgaRepository.save(current);
        }
        int nextTur = dalgaRepository.countByBasvuruDonemiId(donem.getId()) + 1;
        BasvuruDalga created = createDalgaEntity(
                donem,
                nextTur,
                request.ogrenciBaslangicTarihi(),
                request.ogrenciBitisTarihi(),
                true
        );
        donem.setOgrenciBaslangicTarihi(request.ogrenciBaslangicTarihi());
        donem.setOgrenciBitisTarihi(request.ogrenciBitisTarihi());
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.DALGA_AC, "DONEM", donem.getId(),
                "Yeni başvuru turu açıldı: " + created.getAd(), created.getAd());
        return toResponse(created);
    }

    @Transactional
    public BasvuruDalgaResponse tamamla(Long dalgaId, String adminUsername) {
        BasvuruDalga dalga = dalgaRepository.findById(dalgaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru turu bulunamadı."));
        if (!dalga.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca aktif tur tamamlanabilir.");
        }
        if (!dalga.isKesinOnaylandi()) {
            throw new ApiException(HttpStatus.CONFLICT, "Kesin liste onaylanmadan tur tamamlanamaz.");
        }
        if (!dalga.isImzaBildirimiGonderildi()) {
            throw new ApiException(HttpStatus.CONFLICT, "İmza bildirimi gönderilmeden tur tamamlanamaz.");
        }
        dalga.setDurum(BasvuruDalgaDurum.TAMAMLANDI);
        dalga.setAktif(false);
        dalgaRepository.save(dalga);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.DALGA_TAMAMLA, "DONEM",
                dalga.getBasvuruDonemi().getId(), "Başvuru turu tamamlandı: " + dalga.getAd(), dalga.getAd());
        return toResponse(dalga);
    }

    private BasvuruDalga createDalgaEntity(
            BasvuruDonemi donem,
            int turNo,
            LocalDate baslangic,
            LocalDate bitis,
            boolean aktif
    ) {
        BasvuruDalga dalga = new BasvuruDalga();
        dalga.setBasvuruDonemi(donem);
        dalga.setTurNo(turNo);
        dalga.setAd(turNo + ". başvuru turu");
        dalga.setDurum(BasvuruDalgaDurum.ACIK);
        dalga.setAktif(aktif);
        dalga.setOgrenciBaslangicTarihi(baslangic);
        dalga.setOgrenciBitisTarihi(bitis);
        return dalgaRepository.save(dalga);
    }

    private BasvuruDalga ensureInitialDalgaReadOnly(BasvuruDonemi donem) {
        return dalgaRepository.findByBasvuruDonemiIdOrderByTurNoAsc(donem.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Başvuru turu tanımlı değil."));
    }

    private boolean isStudentAccessOpen(BasvuruDalga dalga) {
        if (dalga.getDurum() != BasvuruDalgaDurum.ACIK) {
            return false;
        }
        if (dalga.getOgrenciBaslangicTarihi() == null || dalga.getOgrenciBitisTarihi() == null) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return !today.isBefore(dalga.getOgrenciBaslangicTarihi())
                && !today.isAfter(dalga.getOgrenciBitisTarihi());
    }

    private boolean isLegacyStudentAccessOpen(BasvuruDonemi donem) {
        if (donem.getOgrenciBaslangicTarihi() == null || donem.getOgrenciBitisTarihi() == null) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return !today.isBefore(donem.getOgrenciBaslangicTarihi())
                && !today.isAfter(donem.getOgrenciBitisTarihi());
    }

    private BasvuruDonemi requireDonem(Long donemId) {
        if (donemId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dönem seçilmelidir.");
        }
        return donemRepository.findById(donemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru dönemi bulunamadı."));
    }

    @Transactional
    public BasvuruDalga saveDalga(BasvuruDalga dalga) {
        return dalgaRepository.save(dalga);
    }

    @Transactional
    public void migrateKayitListesiIfNeeded(BasvuruDonemi donem, boolean kesinOnaylandi, boolean imzaGonderildi,
                                          Instant onayTarihi, String onaylayanAdmin,
                                          Instant kesinYukleme, String kesinYukleyen,
                                          Instant imzaTarihi, String imzaGonderen) {
        BasvuruDalga dalga = ensureInitialDalga(donem);
        if (!dalga.isKesinOnaylandi() && kesinOnaylandi) {
            dalga.setKesinOnaylandi(true);
            dalga.setOnayTarihi(onayTarihi);
            dalga.setOnaylayanAdmin(onaylayanAdmin);
        }
        if (!dalga.isImzaBildirimiGonderildi() && imzaGonderildi) {
            dalga.setImzaBildirimiGonderildi(true);
            dalga.setImzaBildirimiGonderimTarihi(imzaTarihi);
            dalga.setImzaBildirimiGonderenAdmin(imzaGonderen);
        }
        if (dalga.getKesinListeYuklemeTarihi() == null && kesinYukleme != null) {
            dalga.setKesinListeYuklemeTarihi(kesinYukleme);
            dalga.setKesinListeYukleyenAdmin(kesinYukleyen);
        }
        dalgaRepository.save(dalga);
    }

    public BasvuruDalgaResponse toResponse(BasvuruDalga dalga) {
        Long dalgaId = dalga.getId();
        return new BasvuruDalgaResponse(
                dalgaId,
                dalga.getBasvuruDonemi().getId(),
                dalga.getTurNo(),
                dalga.getAd(),
                dalga.getDurum(),
                dalga.isAktif(),
                dalga.getOgrenciBaslangicTarihi(),
                dalga.getOgrenciBitisTarihi(),
                isStudentAccessOpen(dalga),
                dalga.isKesinOnaylandi(),
                dalga.isImzaBildirimiGonderildi(),
                iskurKayitRepository.countByBasvuruDalgaId(dalgaId),
                kesinKayitRepository.countByBasvuruDalgaId(dalgaId),
                dalga.getOlusturmaTarihi()
        );
    }
}
