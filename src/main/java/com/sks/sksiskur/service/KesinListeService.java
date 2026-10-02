package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.domain.BasvuruDalga;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.KesinKayitKaydi;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruRepository;
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
    private final BasvuruDonemiService basvuruDonemiService;
    private final BasvuruDalgaService basvuruDalgaService;
    private final IskurExcelParser excelParser;
    private final AuditLogService auditLogService;

    public KesinListeService(
            KesinKayitKaydiRepository kesinKayitKaydiRepository,
            BasvuruRepository basvuruRepository,
            BasvuruDonemiService basvuruDonemiService,
            BasvuruDalgaService basvuruDalgaService,
            IskurExcelParser excelParser,
            AuditLogService auditLogService
    ) {
        this.kesinKayitKaydiRepository = kesinKayitKaydiRepository;
        this.basvuruRepository = basvuruRepository;
        this.basvuruDonemiService = basvuruDonemiService;
        this.basvuruDalgaService = basvuruDalgaService;
        this.excelParser = excelParser;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public KesinListeUploadResponse upload(Long donemId, MultipartFile file, String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        if (!donem.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca aktif döneme kesin liste yüklenebilir.");
        }
        BasvuruDalga dalga = basvuruDalgaService.requireAktifDalga(donem);
        if (dalga.isImzaBildirimiGonderildi()) {
            throw new ApiException(HttpStatus.CONFLICT, "İmza bildirimi gönderildikten sonra İŞKUR nihai listesi değiştirilemez.");
        }
        long atanan = basvuruRepository.countByStatusAndAtananBirimKoduIsNotNullAndBasvuruDonemiId(
                ApplicationStatus.APPROVED, donem.getId());
        if (atanan > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Birim dağıtımı yapıldıktan sonra nihai liste yeniden yüklenemez.");
        }

        List<IskurExcelParser.ParsedRow> parsed = excelParser.parse(file);
        kesinKayitKaydiRepository.deleteByBasvuruDalgaId(dalga.getId());

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
            kayit.setBasvuruDalga(dalga);
            kayit.setTcKimlikNo(row.tcKimlikNo());
            kayit.setAd(row.ad());
            kayit.setSoyad(row.soyad());
            kayit.setOgrenciNo(row.ogrenciNo());
            kayit.setAdSoyadAnahtar(IskurExcelParser.personKey(row.ad(), row.soyad()));
            kesinKayitKaydiRepository.save(kayit);
        }

        ComparisonResult comparison = applyComparison(donem, dalga);
        if (comparison.eslesen() == 0) {
            long rowCount = kesinKayitKaydiRepository.countByBasvuruDalgaId(dalga.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Yüklenen İŞKUR nihai listesinde onaylı başvuru bulunamadı (dosyadan "
                            + rowCount + " satır okundu, 0 eşleşme). Liste, sistemde onaylı öğrencilerin T.C. / öğrenci no / ad-soyad bilgisiyle aynı olmalıdır. "
                            + "Tam kura sonuç dosyası yerine yalnızca bu dönemde onayladığınız öğrencileri içeren dosyayı kullanın.");
        }
        Instant now = Instant.now();
        dalga.setKesinListeYuklemeTarihi(now);
        dalga.setKesinListeYukleyenAdmin(adminUsername);
        dalga.setKesinOnaylandi(true);
        dalga.setOnayTarihi(now);
        dalga.setOnaylayanAdmin(adminUsername);
        basvuruDalgaService.saveDalga(dalga);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.KESIN_LISTE_YUKLE, "DONEM", donem.getId(),
                "İŞKUR nihai listesi yüklendi: " + dalga.getAd(),
                comparison.eslesen() + " öğrenci imza davetine alındı");

        return new KesinListeUploadResponse(
                donem.getId(),
                kesinKayitKaydiRepository.countByBasvuruDalgaId(dalga.getId()),
                skipped,
                comparison.eslesen(),
                comparison.kesinListedeDegil(),
                comparison.listedeBasvuruEslesmedi(),
                dalga.getKesinListeYuklemeTarihi(),
                adminUsername
        );
    }

    @Transactional(readOnly = true)
    public long countUploaded(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        BasvuruDalga dalga = basvuruDalgaService.requireAktifDalga(donem);
        return kesinKayitKaydiRepository.countByBasvuruDalgaId(dalga.getId());
    }

    @Transactional(readOnly = true)
    public List<KesinKayitKaydi> listUploaded(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        BasvuruDalga dalga = basvuruDalgaService.requireAktifDalga(donem);
        return kesinKayitKaydiRepository.findByBasvuruDalgaIdOrderByAdAscSoyadAsc(dalga.getId());
    }

    @Transactional
    public void resetComparison(BasvuruDonemi donem) {
        BasvuruDalga dalga = basvuruDalgaService.requireAktifDalga(donem);
        kesinKayitKaydiRepository.deleteByBasvuruDalgaId(dalga.getId());
        dalga.setKesinListeYuklemeTarihi(null);
        dalga.setKesinListeYukleyenAdmin(null);
        basvuruDalgaService.saveDalga(dalga);
        for (Basvuru basvuru : approvedBasvurular(donem)) {
            if (isProtectedKesin(basvuru)) {
                continue;
            }
            basvuru.setKayitTuru(null);
            basvuru.setKesinListede(null);
            basvuru.setKayitTarihi(null);
        }
    }

    @Transactional
    public ComparisonResult applyComparison(BasvuruDonemi donem, BasvuruDalga dalga) {
        List<KesinKayitKaydi> kayitlar = kesinKayitKaydiRepository.findByBasvuruDalgaIdOrderByAdAscSoyadAsc(dalga.getId());
        List<Basvuru> approved = approvedBasvurular(donem);
        int eslesen = 0;
        int kesinListedeDegil = 0;
        Instant now = Instant.now();

        for (Basvuru basvuru : approved) {
            if (isProtectedKesin(basvuru)) {
                continue;
            }
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
            boolean matched = approved.stream()
                    .filter(b -> !isProtectedKesin(b))
                    .anyMatch(b -> matchesRow(b.getStudent(), kayit));
            if (!matched) {
                listedeBasvuruEslesmedi++;
            }
        }
        return new ComparisonResult(eslesen, kesinListedeDegil, listedeBasvuruEslesmedi);
    }

    private static boolean isProtectedKesin(Basvuru basvuru) {
        return basvuru.isAssigned() && basvuru.getKayitTuru() == KayitTuru.KESIN;
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
