package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruBelgesiRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.StudentRepository;
import com.sks.sksiskur.web.dto.BasvuruKaydetRequest;
import com.sks.sksiskur.web.dto.BasvuruResponse;
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
    private static final String HALKBANK_CODE = "00012";

    private final StudentRepository studentRepository;
    private final BasvuruRepository basvuruRepository;
    private final BasvuruBelgesiRepository belgeRepository;
    private final FileStorageService fileStorageService;
    private final DtoMapper mapper;
    private final BasvuruDonemiService basvuruDonemiService;
    private final AdminAssignmentService adminAssignmentService;
    private final SgkOcrService sgkOcrService;
    private final BelgeOcrService belgeOcrService;
    private final SozlesmeService sozlesmeService;

    public StudentApplicationService(
            StudentRepository studentRepository,
            BasvuruRepository basvuruRepository,
            BasvuruBelgesiRepository belgeRepository,
            FileStorageService fileStorageService,
            DtoMapper mapper,
            BasvuruDonemiService basvuruDonemiService,
            AdminAssignmentService adminAssignmentService,
            SgkOcrService sgkOcrService,
            BelgeOcrService belgeOcrService,
            SozlesmeService sozlesmeService
    ) {
        this.studentRepository = studentRepository;
        this.basvuruRepository = basvuruRepository;
        this.belgeRepository = belgeRepository;
        this.fileStorageService = fileStorageService;
        this.mapper = mapper;
        this.basvuruDonemiService = basvuruDonemiService;
        this.adminAssignmentService = adminAssignmentService;
        this.sgkOcrService = sgkOcrService;
        this.belgeOcrService = belgeOcrService;
        this.sozlesmeService = sozlesmeService;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse profile(String ogrenciNo) {
        return mapper.toProfile(requireStudent(ogrenciNo));
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
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
    }

    @Transactional
    public BasvuruResponse submit(String ogrenciNo, BasvuruKaydetRequest request) {
        basvuruDonemiService.assertStudentAccessOpen();
        sozlesmeService.tumSozlesmelerKabulEdildiMi(ogrenciNo);
        Basvuru basvuru = getOrCreateEntity(ogrenciNo);
        assertEditable(basvuru);
        applyIban(basvuru, request);
        assertComplete(basvuru);
        basvuru.setStatus(ApplicationStatus.SUBMITTED);
        basvuru.setGonderimTarihi(Instant.now());
        basvuru.setAdminNotu(null);
        adminAssignmentService.assign(basvuru);
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
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
        com.sks.sksiskur.domain.BelgeDogrulamaDurumu dogrulamaDurumu = null;
        if (type == DocumentType.SGK_DOKUMU) {
            try {
                dogrulamaDurumu = sgkOcrService.verify(fileStorageService.resolve(stored.relativePath()), stored.contentType(),
                        basvuru.getBasvuruDonemi().getAylikGelirLimiti(), basvuru.getStudent());
            } catch (ApiException ex) {
                if ("SGK dökümü OCR ile okunamadı; net bir PDF veya görsel yükleyiniz.".equals(ex.getMessage())) {
                    dogrulamaDurumu = com.sks.sksiskur.domain.BelgeDogrulamaDurumu.INCELEME_GEREKLI;
                } else {
                    fileStorageService.deleteQuietly(stored.relativePath());
                    throw ex;
                }
            } catch (RuntimeException ex) {
                fileStorageService.deleteQuietly(stored.relativePath());
                throw ex;
            }
        }
        if (type == DocumentType.KIMLIK_BELGESI || type == DocumentType.OGRENCI_BELGESI ||
                type == DocumentType.ADLI_SICIL || type == DocumentType.IKAMETGAH) {
            try {
                dogrulamaDurumu = belgeOcrService.verify(fileStorageService.resolve(stored.relativePath()), stored.contentType(), basvuru.getStudent(), type);
            } catch (RuntimeException ex) {
                fileStorageService.deleteQuietly(stored.relativePath());
                throw ex;
            }
        }
        if (!householdDocument && type != DocumentType.SGK_DOKUMU) {
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
        belge.setDogrulamaDurumu(dogrulamaDurumu);
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
        Student student = requireStudent(ogrenciNo);
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

    private Student requireStudent(String ogrenciNo) {
        return studentRepository.findByOgrenciNo(ogrenciNo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Öğrenci kaydı bulunamadı."));
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

    private void applyIban(Basvuru basvuru, BasvuruKaydetRequest request) {
        String iban = normalizeIban(request.iban());
        validateHalkbankIban(iban);
        basvuru.setIban(iban);
        String hesapSahibi = request.hesapSahibi() == null || request.hesapSahibi().isBlank()
                ? basvuru.getStudent().getAdSoyad()
                : request.hesapSahibi().trim();
        basvuru.setHesapSahibi(hesapSahibi);
    }

    private void assertComplete(Basvuru basvuru) {
        if (basvuru.getIban() == null || basvuru.getIban().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Halkbank IBAN bilgisi zorunludur.");
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
}
