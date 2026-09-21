package com.sks.sksiskur.config;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.BirimKullanici;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import com.sks.sksiskur.domain.KayitListesi;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.KesinKayitKaydi;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.BasvuruDonemiRepository;
import com.sks.sksiskur.repository.BirimKullaniciRepository;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
import com.sks.sksiskur.repository.KayitListesiRepository;
import com.sks.sksiskur.repository.KesinKayitKaydiRepository;
import com.sks.sksiskur.repository.StudentRepository;
import com.sks.sksiskur.service.IskurExcelParser;
import com.sks.sksiskur.service.KesinListeService;
import com.sks.sksiskur.sicil.WorkUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Component
@Order(20)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final byte[] DEMO_PDF = """
            %PDF-1.4
            1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
            2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj
            3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj
            trailer<</Root 1 0 R>>
            %%EOF
            """.getBytes(StandardCharsets.US_ASCII);

    private final StudentRepository studentRepository;
    private final BasvuruRepository basvuruRepository;
    private final BasvuruDonemiRepository basvuruDonemiRepository;
    private final BirimKullaniciRepository birimKullaniciRepository;
    private final IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository;
    private final KesinKayitKaydiRepository kesinKayitKaydiRepository;
    private final KayitListesiRepository kayitListesiRepository;
    private final KesinListeService kesinListeService;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadRoot;

    public DemoDataInitializer(
            StudentRepository studentRepository,
            BasvuruRepository basvuruRepository,
            BasvuruDonemiRepository basvuruDonemiRepository,
            BirimKullaniciRepository birimKullaniciRepository,
            IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository,
            KesinKayitKaydiRepository kesinKayitKaydiRepository,
            KayitListesiRepository kayitListesiRepository,
            KesinListeService kesinListeService,
            PasswordEncoder passwordEncoder,
            @Value("${app.upload-dir}") String uploadDir
    ) {
        this.studentRepository = studentRepository;
        this.basvuruRepository = basvuruRepository;
        this.basvuruDonemiRepository = basvuruDonemiRepository;
        this.birimKullaniciRepository = birimKullaniciRepository;
        this.iskurBasvuruKaydiRepository = iskurBasvuruKaydiRepository;
        this.kesinKayitKaydiRepository = kesinKayitKaydiRepository;
        this.kayitListesiRepository = kayitListesiRepository;
        this.kesinListeService = kesinListeService;
        this.passwordEncoder = passwordEncoder;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(String... args) {
        try {
            String hash = passwordEncoder.encode(DemoAccounts.STUDENT_PASSWORD);
            WorkUnit sks = DemoAccounts.units().getFirst();
            List<WorkUnit> units = DemoAccounts.units();

            for (int index = 1; index <= DemoAccounts.STUDENT_COUNT; index++) {
                DemoScenario scenario = scenarioFor(index, sks, units);
                seed(scenario, hash);
            }
            seedUnitAccounts();
            seedIskurListesi();
            seedKesinListeDemo();
        } catch (Exception ex) {
            log.error("Test kullanıcıları oluşturulamadı", ex);
        }
    }

    private DemoScenario scenarioFor(int index, WorkUnit sks, List<WorkUnit> units) {
        String ogrenciNo = DemoAccounts.studentNo(index);
        return switch (index) {
            case 1 -> featured(ogrenciNo, "Ahmet", "Yılmaz", "Gaziantep Eğitim Fakültesi", "Sınıf Öğretmenliği",
                    ApplicationStatus.DRAFT, null, null, null);
            case 2 -> featured(ogrenciNo, "Ayşe", "Demir", "Gaziantep Eğitim Fakültesi", "Okul Öncesi Öğretmenliği",
                    ApplicationStatus.SUBMITTED, null, null, null);
            case 3 -> featured(ogrenciNo, "Mehmet", "Kaya", "Teknik Bilimler Meslek Yüksekokulu", "Bilgisayar Programcılığı",
                    ApplicationStatus.APPROVED, null, null, null);
            case 4 -> featured(ogrenciNo, "Fatma", "Şahin", "Gaziantep Eğitim Fakültesi", "İlköğretim Matematik Öğretmenliği",
                    ApplicationStatus.APPROVED, KayitTuru.KESIN, sks, null);
            case 5 -> featured(ogrenciNo, "Ali", "Çelik", "Teknik Bilimler Meslek Yüksekokulu", "Elektrik",
                    ApplicationStatus.APPROVED, null, null, null);
            case 6 -> featured(ogrenciNo, "Elif", "Koç", "Gaziantep Eğitim Fakültesi", "Türkçe Öğretmenliği",
                    ApplicationStatus.REJECTED, null, null, "SGK dökümü güncel değil. Yeniden yükleyiniz.");
            default -> generated(index, ogrenciNo, units);
        };
    }

    private DemoScenario featured(
            String ogrenciNo,
            String ad,
            String soyad,
            String fakulte,
            String bolum,
            ApplicationStatus status,
            KayitTuru kayitTuru,
            WorkUnit birim,
            String adminNotu
    ) {
        return new DemoScenario(ogrenciNo, ad, soyad, fakulte, bolum, status, kayitTuru, birim, adminNotu);
    }

    private DemoScenario generated(int index, String ogrenciNo, List<WorkUnit> units) {
        DemoAccounts.DemoStudentProfile profile = DemoAccounts.profile(index);
        int mod = index % 10;
        ApplicationStatus status = switch (mod) {
            case 0 -> ApplicationStatus.DRAFT;
            case 1, 8, 9 -> ApplicationStatus.SUBMITTED;
            case 2, 3 -> ApplicationStatus.APPROVED;
            case 4 -> ApplicationStatus.APPROVED;
            case 5 -> ApplicationStatus.APPROVED;
            case 6 -> ApplicationStatus.REJECTED;
            case 7 -> ApplicationStatus.RETURNED;
            default -> ApplicationStatus.SUBMITTED;
        };
        KayitTuru kayitTuru = null;
        WorkUnit birim = null;
        if (status == ApplicationStatus.APPROVED) {
            if (mod == 4) {
                kayitTuru = KayitTuru.KESIN;
                birim = units.get((index / 10) % units.size());
            }
        }
        String adminNotu = status == ApplicationStatus.REJECTED
                ? "Demo red kaydı (" + ogrenciNo + ")."
                : status == ApplicationStatus.RETURNED
                ? "Eksik belge. Lütfen yeniden yükleyiniz."
                : null;
        return new DemoScenario(
                ogrenciNo, profile.ad(), profile.soyad(), profile.fakulte(), profile.bolum(),
                status, kayitTuru, birim, adminNotu
        );
    }

    private void seed(DemoScenario scenario, String passwordHash) throws IOException {
        Student existing = studentRepository.findByOgrenciNo(scenario.ogrenciNo()).orElse(null);
        if (existing != null) {
            if (existing.getDemoSifreHash() == null || existing.getDemoSifreHash().isBlank()) {
                existing.setDemoSifreHash(passwordHash);
                studentRepository.save(existing);
            }
            return;
        }
        Student student = new Student();
        student.setOgrenciNo(scenario.ogrenciNo());
        student.setTcKimlikNo(tcFrom(scenario.ogrenciNo()));
        student.setAd(scenario.ad());
        student.setSoyad(scenario.soyad());
        student.setUyruk("T.C.");
        student.setDogumYeri("Gaziantep");
        student.setDogumTarihi("01.01.2003");
        student.setCinsiyet(DemoAccounts.isMaleName(scenario.ad()) ? "E" : "K");
        student.setEgitimDerecesi("Lisans");
        student.setKayitTarihi("15.09.2021");
        student.setOgrenimDurumu("Aktif");
        student.setFakulte(scenario.fakulte());
        student.setBolum(scenario.bolum());
        student.setProgram(scenario.bolum());
        student.setSinif(String.valueOf((Integer.parseInt(scenario.ogrenciNo()) % 4) + 1));
        student.setDurumu("Aktif");
        student.setEposta(scenario.ogrenciNo() + "@ogr.gantep.edu.tr");
        student.setGsm("0532" + scenario.ogrenciNo().substring(scenario.ogrenciNo().length() - 7));
        student.setAdres("Şehitkamil / Gaziantep");
        student.setIl("Gaziantep");
        student.setIlce("Şehitkamil");
        student.setDanisman("Dr. Öğr. Üyesi Deneme Danışman");
        student.setDemoSifreHash(passwordHash);
        student = studentRepository.save(student);

        Basvuru basvuru = new Basvuru();
        basvuru.setStudent(student);
        BasvuruDonemi donem = basvuruDonemiRepository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc().orElseThrow();
        basvuru.setBasvuruDonemi(donem);
        basvuru.setStatus(scenario.status());
        basvuru.setIban(ibanFrom(scenario.ogrenciNo()));
        basvuru.setHesapSahibi(student.getAdSoyad());
        basvuru.setAylikGelir(new java.math.BigDecimal("12500.00"));
        Instant now = Instant.now();
        if (scenario.status() != ApplicationStatus.DRAFT) {
            basvuru.setGonderimTarihi(now.minusSeconds(86400L + Integer.parseInt(scenario.ogrenciNo())));
        }
        if (scenario.status() == ApplicationStatus.APPROVED || scenario.status() == ApplicationStatus.REJECTED) {
            basvuru.setIncelemeTarihi(now.minusSeconds(3600));
            basvuru.setInceleyenAdmin("admin");
        }
        if (scenario.adminNotu() != null) {
            basvuru.setAdminNotu(scenario.adminNotu());
        }
        if (scenario.kayitTuru() != null) {
            basvuru.setKayitTuru(scenario.kayitTuru());
            basvuru.setKesinListede(scenario.kayitTuru() == KayitTuru.KESIN);
            basvuru.setKayitTarihi(now);
        }
        if (scenario.birim() != null) {
            basvuru.setAtananBirimKodu(scenario.birim().kod());
            basvuru.setAtananBirimAdi(scenario.birim().displayName());
            basvuru.setAtamaTuru("FAKULTE");
            basvuru.setAtamaTarihi(now);
        }
        basvuru = basvuruRepository.save(basvuru);
        if (scenario.status() != ApplicationStatus.DRAFT) {
            attachDocuments(basvuru);
            basvuruRepository.save(basvuru);
        }
    }

    private void attachDocuments(Basvuru basvuru) throws IOException {
        Path folder = uploadRoot.resolve("basvuru-" + basvuru.getId());
        Files.createDirectories(folder);
        for (DocumentType type : EnumSet.allOf(DocumentType.class).stream()
                .filter(type -> type != DocumentType.HALKBANK_IBAN)
                .toList()) {
            String stored = type.name().toLowerCase() + "_demo.pdf";
            Path target = folder.resolve(stored);
            Files.write(target, DEMO_PDF);
            BasvuruBelgesi belge = new BasvuruBelgesi();
            belge.setBasvuru(basvuru);
            belge.setBelgeTipi(type);
            if (type == DocumentType.HANE_SGK_DOKUMU) {
                belge.setHaneUyesiAdi("Demo Hane Üyesi");
            }
            belge.setOrijinalAd(type.getLabel() + ".pdf");
            belge.setSaklamaYolu(uploadRoot.relativize(target).toString().replace('\\', '/'));
            belge.setIcerikTipi("application/pdf");
            belge.setBoyutByte((long) DEMO_PDF.length);
            basvuru.getBelgeler().add(belge);
        }
    }

    private void seedKesinListeDemo() {
        BasvuruDonemi donem = basvuruDonemiRepository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc().orElse(null);
        if (donem == null || kesinKayitKaydiRepository.countByBasvuruDonemiId(donem.getId()) > 0) {
            return;
        }
        List<Basvuru> kesinBasvurular = basvuruRepository
                .findByStatusAndKayitTuruAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
                        ApplicationStatus.APPROVED, KayitTuru.KESIN, donem.getId());
        if (kesinBasvurular.isEmpty()) {
            return;
        }
        for (Basvuru basvuru : kesinBasvurular) {
            Student student = basvuru.getStudent();
            KesinKayitKaydi kayit = new KesinKayitKaydi();
            kayit.setBasvuruDonemi(donem);
            kayit.setTcKimlikNo(student.getTcKimlikNo());
            kayit.setAd(student.getAd());
            kayit.setSoyad(student.getSoyad());
            kayit.setOgrenciNo(student.getOgrenciNo());
            kayit.setAdSoyadAnahtar(IskurExcelParser.personKey(student.getAd(), student.getSoyad()));
            kesinKayitKaydiRepository.save(kayit);
        }
        kesinListeService.applyComparison(donem);

        basvuruRepository.findByStatusAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId()).stream()
                .filter(b -> "99010003".equals(b.getStudent().getOgrenciNo()))
                .findFirst()
                .ifPresent(b -> {
                    b.setKayitTuru(null);
                    b.setKesinListede(null);
                    b.setKayitTarihi(null);
                    basvuruRepository.save(b);
                });

        KayitListesi liste = kayitListesiRepository.findByBasvuruDonemiId(donem.getId()).orElseGet(() -> {
            KayitListesi created = new KayitListesi();
            created.setBasvuruDonemi(donem);
            return created;
        });
        liste.setKesinListeYuklemeTarihi(Instant.now());
        liste.setKesinListeYukleyenAdmin("admin");
        liste.setKesinOnaylandi(true);
        liste.setOnayTarihi(Instant.now());
        liste.setOnaylayanAdmin("admin");
        kayitListesiRepository.save(liste);
    }

    private void seedIskurListesi() {
        BasvuruDonemi donem = basvuruDonemiRepository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc().orElse(null);
        if (donem == null) {
            return;
        }
        boolean added = false;
        for (Student student : studentRepository.findAll()) {
            if (student.getDemoSifreHash() == null || student.getDemoSifreHash().isBlank()) {
                continue;
            }
            if (iskurBasvuruKaydiRepository.existsByBasvuruDonemiIdAndOgrenciNo(donem.getId(), student.getOgrenciNo())) {
                continue;
            }
            IskurBasvuruKaydi kayit = new IskurBasvuruKaydi();
            kayit.setBasvuruDonemi(donem);
            kayit.setTcKimlikNo(student.getTcKimlikNo());
            kayit.setAd(student.getAd());
            kayit.setSoyad(student.getSoyad());
            kayit.setOgrenciNo(student.getOgrenciNo());
            kayit.setAdSoyadAnahtar(IskurExcelParser.personKey(student.getAd(), student.getSoyad()));
            iskurBasvuruKaydiRepository.save(kayit);
            added = true;
        }
        if (added) {
            donem.setIskurListeYuklemeTarihi(Instant.now());
        }
    }

    private void seedUnitAccounts() {
        String hash = passwordEncoder.encode(DemoAccounts.UNIT_PASSWORD);
        for (WorkUnit unit : DemoAccounts.units()) {
            if (birimKullaniciRepository.findByUsernameIgnoreCase(unit.kod()).isPresent()) {
                continue;
            }
            BirimKullanici user = new BirimKullanici();
            user.setUsername(unit.kod());
            user.setPasswordHash(hash);
            user.setAdSoyad(unit.displayName());
            user.setBirimKodu(unit.kod());
            user.setBirimAdi(unit.displayName());
            user.setAktif(true);
            birimKullaniciRepository.save(user);
        }
    }

    private static String tcFrom(String ogrenciNo) {
        String digits = ogrenciNo.replaceAll("\\D", "");
        return ("10000000" + digits).substring(("10000000" + digits).length() - 11);
    }

    private static String ibanFrom(String ogrenciNo) {
        String tail = "00000000000000000" + ogrenciNo.replaceAll("\\D", "");
        tail = tail.substring(tail.length() - 17);
        return "TR12" + "00012" + tail;
    }

    private record DemoScenario(
            String ogrenciNo,
            String ad,
            String soyad,
            String fakulte,
            String bolum,
            ApplicationStatus status,
            KayitTuru kayitTuru,
            WorkUnit birim,
            String adminNotu
    ) {
    }
}
