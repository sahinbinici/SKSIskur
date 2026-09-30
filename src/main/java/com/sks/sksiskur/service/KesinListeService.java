package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.domain.KayitListesi;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.KesinKayitKaydi;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.KayitListesiRepository;
import com.sks.sksiskur.repository.KesinKayitKaydiRepository;
import com.sks.sksiskur.web.dto.KesinListeUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KesinListeService {

    private final KesinKayitKaydiRepository kesinKayitKaydiRepository;
    private final BasvuruRepository basvuruRepository;
    private final KayitListesiRepository kayitListesiRepository;
    private final BasvuruDonemiService basvuruDonemiService;
    private final IskurExcelParser excelParser;
    private final AuditLogService auditLogService;

    public KesinListeService(
            KesinKayitKaydiRepository kesinKayitKaydiRepository,
            BasvuruRepository basvuruRepository,
            KayitListesiRepository kayitListesiRepository,
            BasvuruDonemiService basvuruDonemiService,
            IskurExcelParser excelParser,
            AuditLogService auditLogService
    ) {
        this.kesinKayitKaydiRepository = kesinKayitKaydiRepository;
        this.basvuruRepository = basvuruRepository;
        this.kayitListesiRepository = kayitListesiRepository;
        this.basvuruDonemiService = basvuruDonemiService;
        this.excelParser = excelParser;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public KesinListeUploadResponse upload(Long donemId, MultipartFile file, String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        if (!donem.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca aktif döneme kesin liste yüklenebilir.");
        }
        KayitListesi meta = currentListe(donem);
        if (meta.isKesinOnaylandi()) {
            throw new ApiException(HttpStatus.CONFLICT, "Kesin liste onaylandıktan sonra yeniden yüklenemez.");
        }

        List<IskurExcelParser.ParsedRow> parsed = excelParser.parse(file);
        kesinKayitKaydiRepository.deleteByBasvuruDonemiId(donem.getId());

        Set<String> seen = new LinkedHashSet<>();
        int skipped = 0;
        for (IskurExcelParser.ParsedRow row : parsed) {
            String key = dedupeKey(row);
            if (!seen.add(key)) {
                skipped++;
                continue;
            }
            KesinKayitKaydi kayit = new KesinKayitKaydi();
            kayit.setBasvuruDonemi(donem);
            kayit.setTcKimlikNo(row.tcKimlikNo());
            kayit.setAd(row.ad());
            kayit.setSoyad(row.soyad());
            kayit.setOgrenciNo(row.ogrenciNo());
            kayit.setAdSoyadAnahtar(IskurExcelParser.personKey(row.ad(), row.soyad()));
            kesinKayitKaydiRepository.save(kayit);
        }

        ComparisonResult comparison = applyComparison(donem);
        meta.setKesinListeYuklemeTarihi(Instant.now());
        meta.setKesinListeYukleyenAdmin(adminUsername);
        kayitListesiRepository.save(meta);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.KESIN_LISTE_YUKLE, "DONEM", donem.getId(),
                "Kesin liste yüklendi: " + donem.getAd(),
                comparison.eslesen() + " eşleşme, " + comparison.kesinListedeDegil() + " listede yok");

        return new KesinListeUploadResponse(
                donem.getId(),
                kesinKayitKaydiRepository.countByBasvuruDonemiId(donem.getId()),
                skipped,
                comparison.eslesen(),
                comparison.kesinListedeDegil(),
                comparison.listedeBasvuruEslesmedi(),
                meta.getKesinListeYuklemeTarihi(),
                adminUsername
        );
    }

    @Transactional(readOnly = true)
    public long countUploaded(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        return kesinKayitKaydiRepository.countByBasvuruDonemiId(donem.getId());
    }

    @Transactional(readOnly = true)
    public List<KesinKayitKaydi> listUploaded(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        return kesinKayitKaydiRepository.findByBasvuruDonemiIdOrderByAdAscSoyadAsc(donem.getId());
    }

    @Transactional
    public void resetComparison(BasvuruDonemi donem) {
        kesinKayitKaydiRepository.deleteByBasvuruDonemiId(donem.getId());
        KayitListesi meta = currentListe(donem);
        meta.setKesinListeYuklemeTarihi(null);
        meta.setKesinListeYukleyenAdmin(null);
        kayitListesiRepository.save(meta);
        for (Basvuru basvuru : approvedBasvurular(donem)) {
            basvuru.setKayitTuru(null);
            basvuru.setKesinListede(null);
            basvuru.setKayitTarihi(null);
        }
    }

    @Transactional
    public ComparisonResult applyComparison(BasvuruDonemi donem) {
        List<KesinKayitKaydi> kayitlar = kesinKayitKaydiRepository.findByBasvuruDonemiIdOrderByAdAscSoyadAsc(donem.getId());
        List<Basvuru> approved = approvedBasvurular(donem);
        int eslesen = 0;
        int kesinListedeDegil = 0;
        Instant now = Instant.now();

        for (Basvuru basvuru : approved) {
            if (matches(basvuru.getStudent(), kayitlar)) {
                basvuru.setKayitTuru(KayitTuru.KESIN);
                basvuru.setKesinListede(true);
                basvuru.setKayitTarihi(now);
                eslesen++;
            } else {
                basvuru.setKayitTuru(null);
                basvuru.setKesinListede(false);
                basvuru.setKayitTarihi(null);
                kesinListedeDegil++;
            }
        }
        basvuruRepository.saveAll(approved);

        int listedeBasvuruEslesmedi = 0;
        for (KesinKayitKaydi kayit : kayitlar) {
            boolean matched = approved.stream().anyMatch(b -> matchesRow(b.getStudent(), kayit));
            if (!matched) {
                listedeBasvuruEslesmedi++;
            }
        }
        return new ComparisonResult(eslesen, kesinListedeDegil, listedeBasvuruEslesmedi);
    }

    public boolean matches(Student student, List<KesinKayitKaydi> kayitlar) {
        return kayitlar.stream().anyMatch(kayit -> matchesRow(student, kayit));
    }

    private boolean matchesRow(Student student, KesinKayitKaydi kayit) {
        String tc = IskurExcelParser.normalizeTc(student.getTcKimlikNo());
        if (tc != null && tc.equals(kayit.getTcKimlikNo())) {
            return true;
        }
        String ogrenciNo = IskurExcelParser.normalizeOgrenciNo(student.getOgrenciNo());
        if (ogrenciNo != null && ogrenciNo.equals(kayit.getOgrenciNo())) {
            return true;
        }
        String personKey = IskurExcelParser.personKey(student.getAd(), student.getSoyad());
        return !personKey.isBlank() && personKey.equals(kayit.getAdSoyadAnahtar());
    }

    private List<Basvuru> approvedBasvurular(BasvuruDonemi donem) {
        return basvuruRepository.findByStatusAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
    }

    private KayitListesi currentListe(BasvuruDonemi donem) {
        return kayitListesiRepository.findByBasvuruDonemiId(donem.getId()).orElseGet(() -> {
            KayitListesi created = new KayitListesi();
            created.setBasvuruDonemi(donem);
            created.setKesinOnaylandi(false);
            return kayitListesiRepository.save(created);
        });
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

    public record ComparisonResult(int eslesen, int kesinListedeDegil, int listedeBasvuruEslesmedi) {
    }
}
