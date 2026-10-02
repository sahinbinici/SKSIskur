package com.sks.sksiskur.config;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.BasvuruDalga;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.repository.BasvuruDonemiRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
import com.sks.sksiskur.repository.StudentRepository;
import com.sks.sksiskur.service.BasvuruDalgaService;
import com.sks.sksiskur.service.IskurExcelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Component
@Order(25)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class IskurSampleStudentsInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IskurSampleStudentsInitializer.class);
    private static final Path SAMPLE_FILE = Path.of("local-testdata", "sample-100.tsv");
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
    private final IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository;
    private final BasvuruDalgaService basvuruDalgaService;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadRoot;

    public IskurSampleStudentsInitializer(
            StudentRepository studentRepository,
            BasvuruRepository basvuruRepository,
            BasvuruDonemiRepository basvuruDonemiRepository,
            IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository,
            BasvuruDalgaService basvuruDalgaService,
            PasswordEncoder passwordEncoder,
            @Value("${app.upload-dir}") String uploadDir
    ) {
        this.studentRepository = studentRepository;
        this.basvuruRepository = basvuruRepository;
        this.basvuruDonemiRepository = basvuruDonemiRepository;
        this.iskurBasvuruKaydiRepository = iskurBasvuruKaydiRepository;
        this.basvuruDalgaService = basvuruDalgaService;
        this.passwordEncoder = passwordEncoder;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(String... args) {
        if (!Files.exists(SAMPLE_FILE)) {
            log.info("İŞKUR 100 kişilik örnek dosyası yok, atlandı: {}", SAMPLE_FILE.toAbsolutePath());
            return;
        }
        try {
            List<SampleStudent> samples = readSamples(SAMPLE_FILE);
            BasvuruDonemi donem = basvuruDonemiRepository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc().orElse(null);
            if (donem == null || samples.isEmpty()) {
                return;
            }
            BasvuruDalga dalga = basvuruDalgaService.ensureInitialDalga(donem);
            String hash = passwordEncoder.encode(DemoAccounts.STUDENT_PASSWORD);
            int created = 0;
            for (SampleStudent sample : samples) {
                if (studentRepository.findByOgrenciNo(sample.ogrenciNo()).isPresent()) {
                    continue;
                }
                seed(sample, donem, dalga, hash);
                created++;
            }
            if (created > 0) {
                donem.setIskurListeYuklemeTarihi(Instant.now());
                log.info("İŞKUR örnek öğrencileri yüklendi: {} kayıt. Giriş 88010001-88010100 / {}",
                        created, DemoAccounts.STUDENT_PASSWORD);
            }
        } catch (Exception ex) {
            log.error("İŞKUR örnek öğrencileri yüklenemedi", ex);
        }
    }

    private List<SampleStudent> readSamples(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<SampleStudent> samples = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length < 6) {
                continue;
            }
            samples.add(new SampleStudent(
                    cols[0], cols[1], cols[2], cols[3], cols[4], cols[5],
                    cols.length > 6 ? cols[6] : "",
                    cols.length > 7 && !cols[7].isBlank() ? cols[7] : null
            ));
        }
        return samples;
    }

    private void seed(SampleStudent sample, BasvuruDonemi donem, BasvuruDalga dalga, String hash) throws Exception {
        Student student = new Student();
        student.setOgrenciNo(sample.ogrenciNo());
        student.setTcKimlikNo(sample.tcKimlikNo());
        student.setAd(sample.ad());
        student.setSoyad(sample.soyad());
        student.setUyruk("T.C.");
        student.setFakulte(sample.fakulte());
        student.setBolum(sample.bolum());
        student.setProgram(sample.bolum());
        student.setOgrenimDurumu("Aktif");
        student.setDurumu("Aktif");
        student.setCinsiyet(sample.cinsiyet());
        student.setGsm(sample.gsm());
        student.setEposta(sample.ogrenciNo() + "@ogr.gantep.edu.tr");
        student.setDemoSifreHash(hash);
        student = studentRepository.save(student);

        Basvuru basvuru = new Basvuru();
        basvuru.setStudent(student);
        basvuru.setBasvuruDonemi(donem);
        basvuru.setStatus(ApplicationStatus.APPROVED);
        Instant now = Instant.now();
        basvuru.setGonderimTarihi(now.minusSeconds(86400));
        basvuru.setIncelemeTarihi(now.minusSeconds(3600));
        basvuru.setInceleyenAdmin("admin");
        String digits = "0001200000000000000" + sample.ogrenciNo().replaceAll("\\D", "");
        basvuru.setIban("TR12" + digits.substring(digits.length() - 22));
        basvuru.setHesapSahibi(student.getAd() + " " + student.getSoyad());
        basvuru = basvuruRepository.save(basvuru);
        attachDocuments(basvuru);

        if (!iskurBasvuruKaydiRepository.existsByBasvuruDonemiIdAndTcKimlikNo(donem.getId(), sample.tcKimlikNo())) {
            IskurBasvuruKaydi kayit = new IskurBasvuruKaydi();
            kayit.setBasvuruDonemi(donem);
            kayit.setBasvuruDalga(dalga);
            kayit.setTcKimlikNo(sample.tcKimlikNo());
            kayit.setAd(sample.ad());
            kayit.setSoyad(sample.soyad());
            kayit.setOgrenciNo(sample.ogrenciNo());
            kayit.setAdSoyadAnahtar(IskurExcelParser.personKey(sample.ad(), sample.soyad()));
            iskurBasvuruKaydiRepository.save(kayit);
        }
    }

    private void attachDocuments(Basvuru basvuru) throws Exception {
        Path folder = uploadRoot.resolve("basvuru-" + basvuru.getId());
        Files.createDirectories(folder);
        for (DocumentType type : EnumSet.allOf(DocumentType.class)) {
            if (type == DocumentType.HALKBANK_IBAN) {
                continue;
            }
            Path target = folder.resolve(type.name().toLowerCase() + "_sample.pdf");
            Files.write(target, DEMO_PDF);
            BasvuruBelgesi belge = new BasvuruBelgesi();
            belge.setBasvuru(basvuru);
            belge.setBelgeTipi(type);
            if (type == DocumentType.HANE_SGK_DOKUMU) {
                belge.setHaneUyesiAdi("Örnek Hane Üyesi");
            }
            belge.setOrijinalAd(type.getLabel() + ".pdf");
            belge.setSaklamaYolu(uploadRoot.relativize(target).toString().replace('\\', '/'));
            belge.setIcerikTipi("application/pdf");
            belge.setBoyutByte((long) DEMO_PDF.length);
            basvuru.getBelgeler().add(belge);
        }
        basvuruRepository.save(basvuru);
    }

    public record SampleStudent(
            String ogrenciNo,
            String tcKimlikNo,
            String ad,
            String soyad,
            String fakulte,
            String bolum,
            String cinsiyet,
            String gsm
    ) {
    }
}
