package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruBelgesiRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.StudentRepository;
import com.sks.sksiskur.web.dto.BasvuruKaydetRequest;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.IskurBasvuruUygunluk;
import com.sks.sksiskur.web.dto.StudentProfileResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StudentApplicationService {

    private static final Pattern IBAN_PATTERN = Pattern.compile("^TR\\d{24}$");
    private static final Pattern SUBE_KODU_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern HESAP_NUMARASI_PATTERN = Pattern.compile("^\\d{1,16}$");
    private static final String HALKBANK_CODE = "00012";

    private final StudentRepository studentRepository;
    private final BasvuruRepository basvuruRepository;
    private final BasvuruBelgesiRepository belgeRepository;
    private final FileStorageService fileStorageService;
    private final DtoMapper mapper;
    private final BasvuruDonemiService basvuruDonemiService;
    private final AdminAssignmentService adminAssignmentService;
    private final SozlesmeService sozlesmeService;
    private final IskurListeService iskurListeService;
    private final AuditLogService auditLogService;

    public StudentApplicationService(
            StudentRepository studentRepository,
            BasvuruRepository basvuruRepository,
            BasvuruBelgesiRepository belgeRepository,
            FileStorageService fileStorageService,
            DtoMapper mapper,
            BasvuruDonemiService basvuruDonemiService,
            AdminAssignmentService adminAssignmentService,
            SozlesmeService sozlesmeService,
            IskurListeService iskurListeService,
            AuditLogService auditLogService
    ) {
        this.studentRepository = studentRepository;
        this.basvuruRepository = basvuruRepository;
        this.belgeRepository = belgeRepository;
        this.fileStorageService = fileStorageService;
        this.mapper = mapper;
        this.basvuruDonemiService = basvuruDonemiService;
        this.adminAssignmentService = adminAssignmentService;
        this.sozlesmeService = sozlesmeService;
        this.iskurListeService = iskurListeService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse profile(String ogrenciNo) {
        Student student = findStudent(ogrenciNo);
        IskurBasvuruUygunluk uygunluk = iskurListeService.basvuruUygunluk(student);
        return mapper.toProfile(
                student,
                uygunluk.demoOgrenci(),
                uygunluk.basvuruyaUygun(),
                uygunluk.engelMesaji()
        );
    }

    @Transactional
    public BasvuruResponse getOrCreate(String ogrenciNo) {
        basvuruDonemiService.assertStudentAccessOpen();
        sozlesmeService.tumSozlesmelerKabulEdildiMi(ogrenciNo);
        return mapper.toBasvuru(getOrCreateEntity(ogrenciNo));
    }

    @Transactional
    public BasvuruResponse saveDraft(String ogrenciNo, BasvuruKaydetRequest request) {
        basvuruDonemiService.assertStudentAccessOpen();
        sozlesmeService.tumSozlesmelerKabulEdildiMi(ogrenciNo);
        Basvuru basvuru = getOrCreateEntity(ogrenciNo);
        assertEditable(basvuru);
        String iban = normalizeIban(request.iban());
        if (!iban.isBlank()) {
            validateHalkbankIban(iban);
            basvuru.setIban(iban);
        }
        if (request.hesapSahibi() != null && !request.hesapSahibi().isBlank()) {
            basvuru.setHesapSahibi(request.hesapSahibi().trim());
        }
        applyOptionalHesapDetaylari(basvuru, request);
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
    }

    @Transactional
    public BasvuruResponse submit(String ogrenciNo, BasvuruKaydetRequest request) {
        basvuruDonemiService.assertStudentAccessOpen();
        sozlesmeService.tumSozlesmelerKabulEdildiMi(ogrenciNo);
        Basvuru basvuru = getOrCreateEntity(ogrenciNo);
        assertEditable(basvuru);
        applyHesapBilgisi(basvuru, request);
        assertComplete(basvuru);
        basvuru.setStatus(ApplicationStatus.SUBMITTED);
        basvuru.setGonderimTarihi(Instant.now());
        basvuru.setAdminNotu(null);
        adminAssignmentService.assign(basvuru);
        Basvuru saved = basvuruRepository.save(basvuru);
        auditLogService.log(Role.STUDENT, ogrenciNo, saved.getStudent().getAdSoyad(), IslemTuru.BASVURU_GONDERIM, "BASVURU", saved.getId(),
                "Başvuru gönderildi", null);
        return mapper.toBasvuru(saved);
    }

    @Transactional
    public BasvuruResponse uploadDocument(String ogrenciNo, DocumentType type, String haneUyesiAdi, MultipartFile file) {
        basvuruDonemiService.assertStudentAccessOpen();
        sozlesmeService.tumSozlesmelerKabulEdildiMi(ogrenciNo);
        Basvuru basvuru = getOrCreateEntity(ogrenciNo);
        assertEditable(basvuru);
        boolean householdDocument = type == DocumentType.HANE_SGK_DOKUMU;
        if (householdDocument && (haneUyesiAdi == null || haneUyesiAdi.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hane üyesinin adını ve soyadını giriniz.");
        }
        FileStorageService.StoredFile stored = fileStorageService.store(basvuru.getId(), type.name(), file);
        if (!householdDocument && type != DocumentType.SGK_DOKUMU && type != DocumentType.IKAMETGAH) {
            basvuru.getBelgeler().removeIf(existing -> {
                if (existing.getBelgeTipi() == type) {
                    fileStorageService.deleteQuietly(existing.getSaklamaYolu());
                    return true;
                }
                return false;
            });
        }
        BasvuruBelgesi belge = new BasvuruBelgesi();
        belge.setBasvuru(basvuru);
        belge.setBelgeTipi(type);
        belge.setHaneUyesiAdi(householdDocument ? haneUyesiAdi.trim() : null);
        belge.setOrijinalAd(stored.originalName());
        belge.setSaklamaYolu(stored.relativePath());
        belge.setIcerikTipi(stored.contentType());
        belge.setBoyutByte(stored.size());
        belge.setDogrulamaDurumu(null);
        belge.setDogrulamaNotu(null);
        basvuru.getBelgeler().add(belge);
        belgeRepository.save(belge);
        return mapper.toBasvuru(basvuru);
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadDocument(String ogrenciNo, Long belgeId) {
        basvuruDonemiService.assertStudentAccessOpen();
        sozlesmeService.tumSozlesmelerKabulEdildiMi(ogrenciNo);
        Basvuru basvuru = basvuruRepository.findByStudentOgrenciNoAndBasvuruDonemiAktifTrue(ogrenciNo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı."));
        BasvuruBelgesi belge = belgeRepository.findByIdAndBasvuruId(belgeId, basvuru.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Belge bulunamadı."));
        Path path = fileStorageService.resolve(belge.getSaklamaYolu());
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Dosya diskte bulunamadı.");
        }
        return new DocumentDownload(
                new FileSystemResource(path),
                belge.getOrijinalAd(),
                belge.getIcerikTipi() != null ? belge.getIcerikTipi() : "application/octet-stream"
        );
    }

    @Transactional
    public BasvuruResponse deleteDocument(String ogrenciNo, Long belgeId) {
        basvuruDonemiService.assertStudentAccessOpen();
        sozlesmeService.tumSozlesmelerKabulEdildiMi(ogrenciNo);
        Basvuru basvuru = getOrCreateEntity(ogrenciNo);
        assertEditable(basvuru);
        BasvuruBelgesi belge = belgeRepository.findByIdAndBasvuruId(belgeId, basvuru.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Belge bulunamadı."));
        fileStorageService.deleteQuietly(belge.getSaklamaYolu());
        basvuru.getBelgeler().removeIf(item -> item.getId().equals(belgeId));
        return mapper.toBasvuru(basvuru);
    }

    private Basvuru getOrCreateEntity(String ogrenciNo) {
        Student student = requireEligibleStudent(ogrenciNo);
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        return basvuruRepository.findByStudentAndBasvuruDonemiId(student, donem.getId()).orElseGet(() -> {
            Basvuru created = new Basvuru();
            created.setStudent(student);
            created.setBasvuruDonemi(donem);
            created.setStatus(ApplicationStatus.DRAFT);
            created.setHesapSahibi(student.getAdSoyad());
            return basvuruRepository.save(created);
        });
    }

    private Student findStudent(String ogrenciNo) {
        return studentRepository.findByOgrenciNo(ogrenciNo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Öğrenci kaydı bulunamadı."));
    }

    private Student requireEligibleStudent(String ogrenciNo) {
        Student student = findStudent(ogrenciNo);
        iskurListeService.assertEligible(student);
        return student;
    }

    @Transactional
    public BasvuruResponse markAtamaBildirimiOkundu(String ogrenciNo) {
        Basvuru basvuru = basvuruRepository.findByStudentOgrenciNoAndBasvuruDonemiAktifTrue(ogrenciNo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı."));
        basvuru.setAtamaBildirimiOkundu(true);
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
    }

    @Transactional
    public BasvuruResponse markImzaBildirimiOkundu(String ogrenciNo) {
        Basvuru basvuru = basvuruRepository.findByStudentOgrenciNoAndBasvuruDonemiAktifTrue(ogrenciNo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı."));
        basvuru.setImzaBildirimiOkundu(true);
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
    }

    private void assertEditable(Basvuru basvuru) {
        if (basvuru.getBasvuruDonemi() == null || !basvuru.getBasvuruDonemi().isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Başvuru dönemi kapandığı için kayıt değiştirilemez.");
        }
        if (basvuru.isLockedForStudent()) {
            if (basvuru.getStatus() == ApplicationStatus.APPROVED) {
                throw new ApiException(HttpStatus.CONFLICT, "Onaylanan başvuru değiştirilemez.");
            }
            throw new ApiException(HttpStatus.CONFLICT, "İncelemedeki başvuru değiştirilemez.");
        }
    }

    private void applyHesapBilgisi(Basvuru basvuru, BasvuruKaydetRequest request) {
        String iban = normalizeIban(request.iban());
        validateHalkbankIban(iban);
        basvuru.setIban(iban);
        String hesapSahibi = request.hesapSahibi() == null || request.hesapSahibi().isBlank()
                ? basvuru.getStudent().getAdSoyad()
                : request.hesapSahibi().trim();
        basvuru.setHesapSahibi(hesapSahibi);
        String bankaSubeKodu = normalizeDigits(request.bankaSubeKodu());
        validateBankaSubeKodu(bankaSubeKodu);
        basvuru.setBankaSubeKodu(bankaSubeKodu);
        String hesapNumarasi = normalizeDigits(request.hesapNumarasi());
        validateHesapNumarasi(hesapNumarasi);
        basvuru.setHesapNumarasi(hesapNumarasi);
    }

    private void applyOptionalHesapDetaylari(Basvuru basvuru, BasvuruKaydetRequest request) {
        String bankaSubeKodu = normalizeDigits(request.bankaSubeKodu());
        if (!bankaSubeKodu.isBlank()) {
            validateBankaSubeKodu(bankaSubeKodu);
            basvuru.setBankaSubeKodu(bankaSubeKodu);
        }
        String hesapNumarasi = normalizeDigits(request.hesapNumarasi());
        if (!hesapNumarasi.isBlank()) {
            validateHesapNumarasi(hesapNumarasi);
            basvuru.setHesapNumarasi(hesapNumarasi);
        }
    }

    private void assertComplete(Basvuru basvuru) {
        if (basvuru.getIban() == null || basvuru.getIban().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Halkbank IBAN bilgisi zorunludur.");
        }
        if (basvuru.getBankaSubeKodu() == null || basvuru.getBankaSubeKodu().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Banka şube kodu zorunludur.");
        }
        if (basvuru.getHesapNumarasi() == null || basvuru.getHesapNumarasi().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hesap numarası zorunludur.");
        }
        Set<DocumentType> uploaded = basvuru.getBelgeler().stream()
                .map(BasvuruBelgesi::getBelgeTipi)
                .collect(Collectors.toSet());
        for (DocumentType required : requiredDocumentTypes()) {
            if (!uploaded.contains(required)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, required.getLabel() + " yüklenmeden başvuru gönderilemez.");
            }
        }
        if (!uploaded.contains(DocumentType.HANE_SGK_DOKUMU)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Aynı hanede yaşayan en az bir kişinin SGK dökümü yüklenmeden başvuru gönderilemez.");
        }
    }

    private Set<DocumentType> requiredDocumentTypes() {
        return EnumSet.of(DocumentType.IKAMETGAH, DocumentType.SGK_DOKUMU,
                DocumentType.ADLI_SICIL, DocumentType.OGRENCI_BELGESI, DocumentType.KIMLIK_BELGESI);
    }

    private String normalizeIban(String iban) {
        return iban == null ? "" : iban.replaceAll("\\s+", "").toUpperCase();
    }

    private void validateHalkbankIban(String iban) {
        if (!IBAN_PATTERN.matcher(iban).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçerli bir TR IBAN giriniz (26 karakter).");
        }
        if (!HALKBANK_CODE.equals(iban.substring(4, 9))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IBAN Halkbank hesabına ait olmalıdır.");
        }
    }

    private String normalizeDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private void validateBankaSubeKodu(String bankaSubeKodu) {
        if (!SUBE_KODU_PATTERN.matcher(bankaSubeKodu).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Banka şube kodu 4 haneli olmalıdır.");
        }
    }

    private void validateHesapNumarasi(String hesapNumarasi) {
        if (!HESAP_NUMARASI_PATTERN.matcher(hesapNumarasi).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hesap numarası yalnızca rakamlardan oluşmalıdır.");
        }
    }
}
