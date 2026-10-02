package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
import com.sks.sksiskur.repository.BasvuruDonemiRepository;
import com.sks.sksiskur.web.dto.IskurBasvuruUygunluk;
import com.sks.sksiskur.web.dto.IskurListeResponse;
import com.sks.sksiskur.web.dto.IskurListeUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class IskurListeService {

    private static final String LISTEDE_DEGIL_MESAJ =
            "İŞKUR listesinde adınız olmadığı için başvuru yapamazsınız.";

    private final IskurBasvuruKaydiRepository repository;
    private final BasvuruDonemiRepository basvuruDonemiRepository;
    private final BasvuruDonemiService basvuruDonemiService;
    private final IskurExcelParser excelParser;
    private final AuditLogService auditLogService;
    private final BasvuruDalgaService basvuruDalgaService;
    private final boolean demoEnabled;

    public IskurListeService(
            IskurBasvuruKaydiRepository repository,
            BasvuruDonemiRepository basvuruDonemiRepository,
            BasvuruDonemiService basvuruDonemiService,
            IskurExcelParser excelParser,
            AuditLogService auditLogService,
            BasvuruDalgaService basvuruDalgaService,
            @Value("${app.demo.enabled:false}") boolean demoEnabled
    ) {
        this.repository = repository;
        this.basvuruDonemiRepository = basvuruDonemiRepository;
        this.basvuruDonemiService = basvuruDonemiService;
        this.excelParser = excelParser;
        this.auditLogService = auditLogService;
        this.basvuruDalgaService = basvuruDalgaService;
        this.demoEnabled = demoEnabled;
    }

    @Transactional(readOnly = true)
    public IskurListeResponse get(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        long count = repository.countByBasvuruDonemiId(donem.getId());
        List<IskurListeResponse.Satir> preview = repository.findTop50ByBasvuruDonemiIdOrderByAdAscSoyadAsc(donem.getId())
                .stream()
                .map(row -> new IskurListeResponse.Satir(
                        row.getTcKimlikNo(),
                        row.getAd(),
                        row.getSoyad(),
                        row.getOgrenciNo()
                ))
                .toList();
        return new IskurListeResponse(
                donem.getId(),
                donem.getAd(),
                count > 0,
                count,
                donem.getIskurListeYuklemeTarihi(),
                preview
        );
    }

    @Transactional
    public IskurListeUploadResponse upload(Long donemId, MultipartFile file, String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        if (!donem.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca aktif döneme İŞKUR listesi yüklenebilir.");
        }

        List<IskurExcelParser.ParsedRow> parsed = excelParser.parse(file);
        var dalga = basvuruDalgaService.requireAktifDalga(donem);
        repository.deleteByBasvuruDalgaId(dalga.getId());

        Set<String> seen = new LinkedHashSet<>();
        int skipped = 0;
        for (IskurExcelParser.ParsedRow row : parsed) {
            String key = dedupeKey(row);
            if (!seen.add(key)) {
                skipped++;
                continue;
            }
            IskurBasvuruKaydi kayit = new IskurBasvuruKaydi();
            kayit.setBasvuruDonemi(donem);
            kayit.setBasvuruDalga(dalga);
            kayit.setTcKimlikNo(row.tcKimlikNo());
            kayit.setAd(row.ad());
            kayit.setSoyad(row.soyad());
            kayit.setOgrenciNo(row.ogrenciNo());
            kayit.setAdSoyadAnahtar(IskurExcelParser.personKey(row.ad(), row.soyad()));
            repository.save(kayit);
        }

        donem.setIskurListeYuklemeTarihi(Instant.now());
        long saved = repository.countByBasvuruDonemiId(donem.getId());
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.ISKUR_LISTE_YUKLE, "DONEM", donem.getId(),
                "İŞKUR başvuru listesi yüklendi: " + donem.getAd(), saved + " kayıt");
        return new IskurListeUploadResponse(
                donem.getId(),
                saved,
                skipped,
                donem.getIskurListeYuklemeTarihi(),
                adminUsername
        );
    }

    @Transactional(readOnly = true)
    public IskurBasvuruUygunluk basvuruUygunluk(Student student) {
        if (isDemoStudent(student)) {
            return new IskurBasvuruUygunluk(true, true, null);
        }
        BasvuruDonemi donem = basvuruDonemiRepository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc()
                .orElse(null);
        if (donem == null) {
            return new IskurBasvuruUygunluk(false, false, "Aktif başvuru dönemi bulunmuyor.");
        }
        if (repository.countByBasvuruDonemiId(donem.getId()) == 0) {
            return new IskurBasvuruUygunluk(
                    false,
                    false,
                    "Bu dönem için İŞKUR başvuru listesi henüz yüklenmedi. Lütfen SKS yöneticinize başvurun."
            );
        }
        if (!isInList(donem.getId(), student)) {
            return new IskurBasvuruUygunluk(false, false, LISTEDE_DEGIL_MESAJ);
        }
        return new IskurBasvuruUygunluk(false, true, null);
    }

    public boolean isDemoStudent(Student student) {
        return demoEnabled && student.getDemoSifreHash() != null && !student.getDemoSifreHash().isBlank();
    }

    @Transactional(readOnly = true)
    public void assertEligible(Student student) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        assertEligible(donem, student);
    }

    @Transactional(readOnly = true)
    public void assertEligible(BasvuruDonemi donem, Student student) {
        if (isDemoStudent(student)) {
            return;
        }
        if (repository.countByBasvuruDonemiId(donem.getId()) == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Bu dönem için İŞKUR başvuru listesi henüz yüklenmedi. Lütfen SKS yöneticinize başvurun.");
        }
        if (!isInList(donem.getId(), student)) {
            throw new ApiException(HttpStatus.FORBIDDEN, LISTEDE_DEGIL_MESAJ);
        }
    }

    @Transactional(readOnly = true)
    public boolean isInList(Long donemId, Student student) {
        String tc = IskurExcelParser.normalizeTc(student.getTcKimlikNo());
        if (tc != null && repository.existsByBasvuruDonemiIdAndTcKimlikNo(donemId, tc)) {
            return true;
        }
        String ogrenciNo = IskurExcelParser.normalizeOgrenciNo(student.getOgrenciNo());
        if (ogrenciNo != null && repository.existsByBasvuruDonemiIdAndOgrenciNo(donemId, ogrenciNo)) {
            return true;
        }
        String personKey = IskurExcelParser.personKey(student.getAd(), student.getSoyad());
        return !personKey.isBlank() && repository.existsByBasvuruDonemiIdAndAdSoyadAnahtar(donemId, personKey);
    }

    private static String dedupeKey(IskurExcelParser.ParsedRow row) {
        if (row.tcKimlikNo() != null) {
            return "tc:" + row.tcKimlikNo();
        }
        if (row.ogrenciNo() != null) {
            return "no:" + row.ogrenciNo();
        }
        return "ad:" + IskurExcelParser.personKey(row.ad(), row.soyad());
    }
}
