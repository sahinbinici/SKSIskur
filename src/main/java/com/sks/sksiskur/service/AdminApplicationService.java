package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.KesinKayitKaydi;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruBelgesiRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.web.dto.AdminOzetResponse;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.BelgeYuklemeFiltre;
import com.sks.sksiskur.web.dto.KayitListeFiltre;
import com.sks.sksiskur.web.dto.KayitListesiResponse;
import com.sks.sksiskur.web.dto.ReviewRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminApplicationService {

    private final BasvuruRepository basvuruRepository;
    private final BasvuruBelgesiRepository belgeRepository;
    private final FileStorageService fileStorageService;
    private final DtoMapper mapper;
    private final BasvuruDonemiService basvuruDonemiService;
    private final KesinListeService kesinListeService;
    private final ImzaBildirimiService imzaBildirimiService;
    private final BasvuruDalgaService basvuruDalgaService;
    private final AuditLogService auditLogService;
    private final AdminBelgeOcrService adminBelgeOcrService;

    public AdminApplicationService(
            BasvuruRepository basvuruRepository,
            BasvuruBelgesiRepository belgeRepository,
            FileStorageService fileStorageService,
            DtoMapper mapper,
            BasvuruDonemiService basvuruDonemiService,
            KesinListeService kesinListeService,
            ImzaBildirimiService imzaBildirimiService,
            BasvuruDalgaService basvuruDalgaService,
            AuditLogService auditLogService,
            AdminBelgeOcrService adminBelgeOcrService
    ) {
        this.basvuruRepository = basvuruRepository;
        this.belgeRepository = belgeRepository;
        this.fileStorageService = fileStorageService;
        this.mapper = mapper;
        this.basvuruDonemiService = basvuruDonemiService;
        this.kesinListeService = kesinListeService;
        this.imzaBildirimiService = imzaBildirimiService;
        this.basvuruDalgaService = basvuruDalgaService;
        this.auditLogService = auditLogService;
        this.adminBelgeOcrService = adminBelgeOcrService;
    }

    @Transactional(readOnly = true)
    public AdminOzetResponse ozet(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        var dalga = basvuruDalgaService.requireAktifDalga(donem);
        boolean kesinListeOnaylandi = dalga.isKesinOnaylandi();
        List<Basvuru> approved = basvuruRepository.findByStatusAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
        long kesin = approved.stream().filter(b -> Boolean.TRUE.equals(b.getKesinListede())).count();
        long kesinListedeDegil = approved.stream().filter(b -> Boolean.FALSE.equals(b.getKesinListede())).count();
        long kayitBekleyen = approved.stream().filter(b -> b.getKesinListede() == null).count();
        return new AdminOzetResponse(
                basvuruRepository.countByBasvuruDonemiId(donem.getId()),
                basvuruRepository.countByStatusAndBasvuruDonemiId(ApplicationStatus.DRAFT, donem.getId()),
                basvuruRepository.countByStatusAndBasvuruDonemiId(ApplicationStatus.SUBMITTED, donem.getId()),
                approved.size(),
                basvuruRepository.countByStatusAndBasvuruDonemiId(ApplicationStatus.REJECTED, donem.getId()),
                basvuruRepository.countByStatusAndAtananBirimKoduIsNotNullAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId()),
                kesin,
                0,
                kayitBekleyen,
                kesinListeOnaylandi
        );
    }

    @Transactional(readOnly = true)
    public List<BasvuruResponse> list(
            Long donemId,
            ApplicationStatus status,
            String query,
            String assignedTo,
            DocumentType belgeTipi,
            BelgeYuklemeFiltre belgeYukleme,
            String fakulte
    ) {
        return filterByBelge(
                searchBasvurular(donemId, status, query, assignedTo, fakulte).stream().map(mapper::toBasvuru).toList(),
                belgeTipi,
                belgeYukleme
        );
    }

    @Transactional(readOnly = true)
    public List<Basvuru> listBasvurular(
            Long donemId,
            ApplicationStatus status,
            String query,
            String assignedTo,
            DocumentType belgeTipi,
            BelgeYuklemeFiltre belgeYukleme,
            String fakulte
    ) {
        return filterBasvurularByBelge(searchBasvurular(donemId, status, query, assignedTo, fakulte), belgeTipi, belgeYukleme);
    }

    @Transactional(readOnly = true)
    public List<String> fakulteler(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        return basvuruRepository.distinctFakulteler(donem.getId());
    }

    private List<Basvuru> searchBasvurular(
            Long donemId,
            ApplicationStatus status,
            String query,
            String assignedTo,
            String fakulte
    ) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        String q = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        String faculty = fakulte == null || fakulte.isBlank() ? "" : fakulte.trim();
        return assignedTo == null
                ? basvuruRepository.search(donem.getId(), status, q, faculty)
                : basvuruRepository.searchAssignedTo(donem.getId(), status, q, faculty, assignedTo);
    }

    static List<BasvuruResponse> filterByBelge(
            List<BasvuruResponse> items,
            DocumentType belgeTipi,
            BelgeYuklemeFiltre belgeYukleme
    ) {
        if (belgeTipi == null || belgeYukleme == null || belgeYukleme == BelgeYuklemeFiltre.TUMU) {
            return items;
        }
        return items.stream()
                .filter(item -> matchesBelgeFilter(item.belgeler().stream().anyMatch(belge -> belge.belgeTipi() == belgeTipi), belgeYukleme))
                .toList();
    }

    static List<Basvuru> filterBasvurularByBelge(
            List<Basvuru> items,
            DocumentType belgeTipi,
            BelgeYuklemeFiltre belgeYukleme
    ) {
        if (belgeTipi == null || belgeYukleme == null || belgeYukleme == BelgeYuklemeFiltre.TUMU) {
            return items;
        }
        return items.stream()
                .filter(item -> matchesBelgeFilter(
                        item.getBelgeler().stream().anyMatch(belge -> belge.getBelgeTipi() == belgeTipi),
                        belgeYukleme
                ))
                .toList();
    }

    private static boolean matchesBelgeFilter(boolean hasBelge, BelgeYuklemeFiltre belgeYukleme) {
        return belgeYukleme == BelgeYuklemeFiltre.VAR ? hasBelge : !hasBelge;
    }

    @Transactional
    public BasvuruResponse get(Long id) {
        Basvuru basvuru = require(id);
        adminBelgeOcrService.reviewPendingDocuments(basvuru);
        return mapper.toBasvuru(basvuru);
    }

    @Transactional
    public BasvuruResponse refreshOcr(Long id) {
        Basvuru basvuru = require(id);
        adminBelgeOcrService.refreshDocuments(basvuru);
        return mapper.toBasvuru(basvuru);
    }

    @Transactional
    public BasvuruResponse approve(Long id, String adminUsername) {
        Basvuru basvuru = require(id);
        assertActivePeriod(basvuru);
        if (basvuru.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca incelemedeki başvurular onaylanabilir.");
        }
        Set<DocumentType> uploaded = basvuru.getBelgeler().stream()
                .map(BasvuruBelgesi::getBelgeTipi)
                .collect(Collectors.toSet());
        for (DocumentType required : EnumSet.of(DocumentType.IKAMETGAH, DocumentType.SGK_DOKUMU,
                DocumentType.ADLI_SICIL, DocumentType.OGRENCI_BELGESI, DocumentType.KIMLIK_BELGESI)) {
            if (!uploaded.contains(required)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, required.getLabel() + " eksik olduğu için onaylanamaz.");
            }
        }
        if (!uploaded.contains(DocumentType.HANE_SGK_DOKUMU)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aynı hanede yaşayan en az bir kişinin SGK dökümü eksik olduğu için onaylanamaz.");
        }
        if (basvuru.getIban() == null || basvuru.getIban().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IBAN eksik olduğu için onaylanamaz.");
        }
        basvuru.setStatus(ApplicationStatus.APPROVED);
        basvuru.setIncelemeTarihi(Instant.now());
        basvuru.setInceleyenAdmin(adminUsername);
        basvuru.setAdminNotu(null);
        basvuru.setKayitTuru(null);
        basvuru.setKesinListede(null);
        basvuru.setKayitTarihi(null);
        Basvuru saved = basvuruRepository.save(basvuru);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.BASVURU_ONAY, "BASVURU", saved.getId(),
                "Başvuru onaylandı: " + saved.getStudent().getOgrenciNo(), null);
        return mapper.toBasvuru(saved);
    }

    @Transactional
    public BasvuruResponse reject(Long id, String adminUsername, ReviewRequest request) {
        Basvuru basvuru = require(id);
        assertActivePeriod(basvuru);
        if (basvuru.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca incelemedeki başvurular reddedilebilir.");
        }
        basvuru.setStatus(ApplicationStatus.REJECTED);
        basvuru.setIncelemeTarihi(Instant.now());
        basvuru.setInceleyenAdmin(adminUsername);
        basvuru.setAdminNotu(request.not().trim());
        Basvuru saved = basvuruRepository.save(basvuru);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.BASVURU_RED, "BASVURU", saved.getId(),
                "Başvuru reddedildi: " + saved.getStudent().getOgrenciNo(), request.not().trim());
        return mapper.toBasvuru(saved);
    }

    @Transactional
    public BasvuruResponse returnToStudent(Long id, String adminUsername, String note) {
        Basvuru basvuru = require(id);
        assertActivePeriod(basvuru);
        if (basvuru.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca incelemedeki başvurular öğrenciye iade edilebilir.");
        }
        basvuru.setStatus(ApplicationStatus.RETURNED);
        basvuru.setIncelemeTarihi(Instant.now());
        basvuru.setInceleyenAdmin(adminUsername);
        basvuru.setAdminNotu(note.trim());
        Basvuru saved = basvuruRepository.save(basvuru);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.BASVURU_IADE, "BASVURU", saved.getId(),
                "Başvuru iade edildi: " + saved.getStudent().getOgrenciNo(), note.trim());
        return mapper.toBasvuru(saved);
    }

    @Transactional(readOnly = true)
    public KayitListesiResponse kayitListesi(KayitListeFiltre filtre) {
        return toKayitResponse(basvuruDonemiService.requireActive(), filtre == null ? KayitListeFiltre.TUMU : filtre);
    }

    @Transactional
    public KayitListesiResponse onaylaKesinListe(String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        var dalga = basvuruDalgaService.requireAktifDalga(donem);
        if (dalga.isKesinOnaylandi()) {
            return toKayitResponse(donem, KayitListeFiltre.TUMU);
        }
        if (kesinListeService.countUploaded(donem.getId()) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kesin liste onaylanmadan önce İŞKUR'dan gelen kesin liste yüklenmelidir.");
        }
        List<Basvuru> approved = basvuruRepository.findByStatusAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
        long kesin = approved.stream().filter(b -> Boolean.TRUE.equals(b.getKesinListede())).count();
        if (kesin == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kesin listede eşleşen en az bir onaylı başvuru olmalıdır.");
        }
        dalga.setKesinOnaylandi(true);
        dalga.setOnayTarihi(Instant.now());
        dalga.setOnaylayanAdmin(adminUsername);
        basvuruDalgaService.saveDalga(dalga);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.KESIN_LISTE_ONAY, "DONEM", donem.getId(),
                "Kesin liste onaylandı: " + dalga.getAd(), null);
        return toKayitResponse(donem, KayitListeFiltre.TUMU);
    }

    @Transactional
    public KayitListesiResponse geriAlKesinListe(String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        var dalga = basvuruDalgaService.requireAktifDalga(donem);
        if (!dalga.isKesinOnaylandi()) {
            kesinListeService.resetComparison(donem);
            return toKayitResponse(donem, KayitListeFiltre.TUMU);
        }
        long atanan = basvuruRepository.countByStatusAndAtananBirimKoduIsNotNullAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
        if (atanan > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Birim dağıtımı yapıldıktan sonra kesin liste onayı geri alınamaz.");
        }
        dalga.setKesinOnaylandi(false);
        dalga.setOnayTarihi(null);
        dalga.setOnaylayanAdmin(null);
        dalga.setImzaBildirimiGonderildi(false);
        dalga.setImzaBildirimiGonderimTarihi(null);
        dalga.setImzaBildirimiGonderenAdmin(null);
        basvuruDalgaService.saveDalga(dalga);
        kesinListeService.resetComparison(donem);
        imzaBildirimiService.resetForActiveDalga(donem);
        auditLogService.log(Role.ADMIN, adminUsername, adminUsername, IslemTuru.KESIN_LISTE_GERI_AL, "DONEM", donem.getId(),
                "Kesin liste onayı geri alındı: " + dalga.getAd(), null);
        return toKayitResponse(donem, KayitListeFiltre.TUMU);
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(Long basvuruId, Long belgeId) {
        BasvuruBelgesi belge = belgeRepository.findByIdAndBasvuruId(belgeId, basvuruId)
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

    private KayitListesiResponse toKayitResponse(BasvuruDonemi donem, KayitListeFiltre filtre) {
        var dalga = basvuruDalgaService.requireAktifDalga(donem);
        List<Basvuru> approved = basvuruRepository.findByStatusAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
        List<KayitListesiResponse.Satir> ogrenciler = approved.stream()
                .sorted(Comparator
                        .comparing((Basvuru b) -> b.getStudent().getSoyad(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(b -> b.getStudent().getAd(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toSatir)
                .filter(row -> matchesFilter(row, filtre))
                .toList();

        List<KesinKayitKaydi> uploaded = kesinListeService.listUploaded(donem.getId());
        List<KayitListesiResponse.ListedeEslesmeyen> listedeEslesmeyenler = uploaded.stream()
                .filter(kayit -> approved.stream().noneMatch(b -> kesinListeService.matches(b.getStudent(), List.of(kayit))))
                .map(kayit -> new KayitListesiResponse.ListedeEslesmeyen(
                        kayit.getTcKimlikNo(), kayit.getAd(), kayit.getSoyad(), kayit.getOgrenciNo()))
                .toList();

        long kesin = approved.stream().filter(b -> Boolean.TRUE.equals(b.getKesinListede())).count();
        long kesinListedeDegil = approved.stream().filter(b -> Boolean.FALSE.equals(b.getKesinListede())).count();
        long bekleyen = approved.stream().filter(b -> b.getKesinListede() == null).count();

        return new KayitListesiResponse(
                !uploaded.isEmpty(),
                dalga.getKesinListeYuklemeTarihi(),
                dalga.getKesinListeYukleyenAdmin(),
                approved.size(),
                kesin,
                kesinListedeDegil,
                bekleyen,
                listedeEslesmeyenler.size(),
                dalga.isKesinOnaylandi(),
                dalga.getOnayTarihi(),
                dalga.getOnaylayanAdmin(),
                dalga.isKesinOnaylandi(),
                dalga.isImzaBildirimiGonderildi(),
                dalga.getImzaBildirimiGonderimTarihi(),
                dalga.getImzaBildirimiGonderenAdmin(),
                basvuruDalgaService.toResponse(dalga),
                ogrenciler,
                listedeEslesmeyenler
        );
    }

    private KayitListesiResponse.Satir toSatir(Basvuru b) {
        return new KayitListesiResponse.Satir(
                b.getId(),
                b.getStudent().getOgrenciNo(),
                b.getStudent().getTcKimlikNo(),
                b.getStudent().getAd(),
                b.getStudent().getSoyad(),
                b.getStudent().getAdSoyad(),
                b.getStudent().getFakulte(),
                b.getStudent().getProgram(),
                b.getStudent().getBolum(),
                b.getKesinListede(),
                b.getKayitTuru(),
                b.getAtananBirimAdi()
        );
    }

    private boolean matchesFilter(KayitListesiResponse.Satir row, KayitListeFiltre filtre) {
        return switch (filtre) {
            case TUMU -> true;
            case KESIN_LISTEDE -> Boolean.TRUE.equals(row.kesinListede());
            case KESIN_LISTEDE_DEGIL -> Boolean.FALSE.equals(row.kesinListede());
            case ONAYLI_BASVURU -> row.kesinListede() == null;
        };
    }

    private Basvuru require(Long id) {
        return basvuruRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı."));
    }

    private void assertActivePeriod(Basvuru basvuru) {
        if (basvuru.getBasvuruDonemi() == null || !basvuru.getBasvuruDonemi().isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Kapalı başvuru dönemindeki kayıt değiştirilemez.");
        }
    }
}
