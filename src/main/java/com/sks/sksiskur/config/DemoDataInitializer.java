package com.sks.sksiskur.config;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.BirimKullanici;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.BasvuruDonemiRepository;
import com.sks.sksiskur.repository.BirimKullaniciRepository;
import com.sks.sksiskur.repository.StudentRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final Path uploadRoot;

    public DemoDataInitializer(
            StudentRepository studentRepository,
            BasvuruRepository basvuruRepository,
            BasvuruDonemiRepository basvuruDonemiRepository,
            BirimKullaniciRepository birimKullaniciRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.upload-dir}") String uploadDir
    ) {
        this.studentRepository = studentRepository;
        this.basvuruRepository = basvuruRepository;
        this.basvuruDonemiRepository = basvuruDonemiRepository;
        this.birimKullaniciRepository = birimKullaniciRepository;
        this.passwordEncoder = passwordEncoder;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(String... args) {
        try {
            String hash = passwordEncoder.encode(DemoAccounts.STUDENT_PASSWORD);
            WorkUnit sks = DemoAccounts.units().getFirst();

            seed("99010001", "Ahmet", "Yılmaz", "Gaziantep Eğitim Fakültesi", "Sınıf Öğretmenliği",
                    ApplicationStatus.DRAFT, null, null, hash, null);
            seed("99010002", "Ayşe", "Demir", "Gaziantep Eğitim Fakültesi", "Okul Öncesi Öğretmenliği",
                    ApplicationStatus.SUBMITTED, null, null, hash, null);
            seed("99010003", "Mehmet", "Kaya", "Teknik Bilimler Meslek Yüksekokulu", "Bilgisayar Programcılığı",
                    ApplicationStatus.APPROVED, null, null, hash, null);
            seed("99010004", "Fatma", "Şahin", "Gaziantep Eğitim Fakültesi", "İlköğretim Matematik Öğretmenliği",
                    ApplicationStatus.APPROVED, KayitTuru.KESIN, sks, hash, null);
            seed("99010005", "Ali", "Çelik", "Teknik Bilimler Meslek Yüksekokulu", "Elektrik",
                    ApplicationStatus.APPROVED, KayitTuru.YEDEK, null, hash, null);
            seed("99010006", "Elif", "Koç", "Gaziantep Eğitim Fakültesi", "Türkçe Öğretmenliği",
                    ApplicationStatus.REJECTED, null, null, hash, "SGK dökümü güncel değil. Yeniden yükleyiniz.");
            seedUnitAccounts();
        } catch (Exception ex) {
            log.error("Test kullanıcıları oluşturulamadı", ex);
        }
    }

    private void seed(
            String ogrenciNo,
            String ad,
            String soyad,
            String fakulte,
            String bolum,
            ApplicationStatus status,
            KayitTuru kayitTuru,
            WorkUnit birim,
            String passwordHash,
            String adminNotu
    ) throws IOException {
        Student existing = studentRepository.findByOgrenciNo(ogrenciNo).orElse(null);
        if (existing != null) {
            if (existing.getDemoSifreHash() == null || existing.getDemoSifreHash().isBlank()) {
                existing.setDemoSifreHash(passwordHash);
                studentRepository.save(existing);
            }
            return;
        }
        Student student = new Student();
        student.setOgrenciNo(ogrenciNo);
        student.setTcKimlikNo(tcFrom(ogrenciNo));
        student.setAd(ad);
        student.setSoyad(soyad);
        student.setUyruk("T.C.");
        student.setDogumYeri("Gaziantep");
        student.setDogumTarihi("01.01.2003");
        student.setCinsiyet(List.of("Ahmet", "Mehmet", "Ali").contains(ad) ? "E" : "K");
        student.setEgitimDerecesi("Lisans");
        student.setKayitTarihi("15.09.2021");
        student.setOgrenimDurumu("Aktif");
        student.setFakulte(fakulte);
        student.setBolum(bolum);
        student.setProgram(bolum);
        student.setSinif("3");
        student.setDurumu("Aktif");
        student.setEposta(ogrenciNo + "@ogr.gantep.edu.tr");
        student.setGsm("05320000000");
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
        basvuru.setStatus(status);
        basvuru.setIban(ibanFrom(ogrenciNo));
        basvuru.setHesapSahibi(student.getAdSoyad());
        basvuru.setAylikGelir(new java.math.BigDecimal("12500.00"));
        Instant now = Instant.now();
        if (status != ApplicationStatus.DRAFT) {
            basvuru.setGonderimTarihi(now.minusSeconds(86400));
        }
        if (status == ApplicationStatus.APPROVED || status == ApplicationStatus.REJECTED) {
            basvuru.setIncelemeTarihi(now.minusSeconds(3600));
            basvuru.setInceleyenAdmin("admin");
        }
        if (adminNotu != null) {
            basvuru.setAdminNotu(adminNotu);
        }
        if (kayitTuru != null) {
            basvuru.setKayitTuru(kayitTuru);
            basvuru.setKayitTarihi(now);
        }
        if (birim != null) {
            basvuru.setAtananBirimKodu(birim.kod());
            basvuru.setAtananBirimAdi(birim.displayName());
            basvuru.setAtamaTuru("FAKULTE");
            basvuru.setAtamaTarihi(now);
        }
        basvuru = basvuruRepository.save(basvuru);
        if (status != ApplicationStatus.DRAFT) {
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
}
