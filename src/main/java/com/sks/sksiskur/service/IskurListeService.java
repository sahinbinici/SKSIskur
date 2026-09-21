package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
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

    private final IskurBasvuruKaydiRepository repository;
    private final BasvuruDonemiService basvuruDonemiService;
    private final IskurExcelParser excelParser;
    private final boolean demoEnabled;

    public IskurListeService(
            IskurBasvuruKaydiRepository repository,
            BasvuruDonemiService basvuruDonemiService,
            IskurExcelParser excelParser,
            @Value("${app.demo.enabled:false}") boolean demoEnabled
    ) {
        this.repository = repository;
        this.basvuruDonemiService = basvuruDonemiService;
        this.excelParser = excelParser;
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
        repository.deleteByBasvuruDonemiId(donem.getId());

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
            kayit.setTcKimlikNo(row.tcKimlikNo());
            kayit.setAd(row.ad());
            kayit.setSoyad(row.soyad());
            kayit.setOgrenciNo(row.ogrenciNo());
            kayit.setAdSoyadAnahtar(IskurExcelParser.personKey(row.ad(), row.soyad()));
            repository.save(kayit);
        }

        donem.setIskurListeYuklemeTarihi(Instant.now());
        long saved = repository.countByBasvuruDonemiId(donem.getId());
        return new IskurListeUploadResponse(
                donem.getId(),
                saved,
                skipped,
                donem.getIskurListeYuklemeTarihi(),
                adminUsername
        );
    }

    @Transactional(readOnly = true)
    public void assertEligible(Student student) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        assertEligible(donem, student);
    }

    @Transactional(readOnly = true)
    public void assertEligible(BasvuruDonemi donem, Student student) {
        if (demoEnabled && student.getDemoSifreHash() != null && !student.getDemoSifreHash().isBlank()) {
            return;
        }
        if (repository.countByBasvuruDonemiId(donem.getId()) == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Bu dönem için İŞKUR başvuru listesi henüz yüklenmedi. Lütfen SKS yöneticinize başvurun.");
        }
        if (!isInList(donem.getId(), student)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "İŞKUR başvuru listesinde kaydınız bulunamadı. Bu dönem için başvuru yapamazsınız.");
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
