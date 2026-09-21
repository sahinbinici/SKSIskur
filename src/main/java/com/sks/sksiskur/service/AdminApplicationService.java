package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.KayitListesi;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.KesinKayitKaydi;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruBelgesiRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.KayitListesiRepository;
import com.sks.sksiskur.web.dto.AdminOzetResponse;
import com.sks.sksiskur.web.dto.BasvuruResponse;
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
    private final KayitListesiRepository kayitListesiRepository;
    private final FileStorageService fileStorageService;
    private final DtoMapper mapper;
    private final BasvuruDonemiService basvuruDonemiService;
    private final KesinListeService kesinListeService;

    public AdminApplicationService(
            BasvuruRepository basvuruRepository,
            BasvuruBelgesiRepository belgeRepository,
            KayitListesiRepository kayitListesiRepository,
            FileStorageService fileStorageService,
            DtoMapper mapper,
            BasvuruDonemiService basvuruDonemiService,
            KesinListeService kesinListeService
    ) {
        this.basvuruRepository = basvuruRepository;
        this.belgeRepository = belgeRepository;
        this.kayitListesiRepository = kayitListesiRepository;
        this.fileStorageService = fileStorageService;
        this.mapper = mapper;
        this.basvuruDonemiService = basvuruDonemiService;
        this.kesinListeService = kesinListeService;
    }

    @Transactional(readOnly = true)
    public AdminOzetResponse ozet(Long donemId) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        KayitListesi liste = kayitListesiRepository.findByBasvuruDonemiId(donem.getId()).orElse(null);
        boolean kesinListeOnaylandi = liste != null && liste.isKesinOnaylandi();
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
    public List<BasvuruResponse> list(Long donemId, ApplicationStatus status, String query, String assignedTo) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(donemId);
        String q = query == null ? "" : query.trim();
        List<Basvuru> applications = assignedTo == null
                ? basvuruRepository.search(donem.getId(), status, q)
                : basvuruRepository.searchAssignedTo(donem.getId(), status, q, assignedTo);
        return applications.stream().map(mapper::toBasvuru).toList();
    }

    @Transactional(readOnly = true)
    public BasvuruResponse get(Long id) {
        return mapper.toBasvuru(require(id));
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
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
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
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
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
        return mapper.toBasvuru(basvuruRepository.save(basvuru));
    }

    @Transactional(readOnly = true)
    public KayitListesiResponse kayitListesi(KayitListeFiltre filtre) {
        return toKayitResponse(basvuruDonemiService.requireActive(), filtre == null ? KayitListeFiltre.TUMU : filtre);
    }

    @Transactional
    public KayitListesiResponse onaylaKesinListe(String adminUsername) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        KayitListesi liste = currentListe(donem);
        if (liste.isKesinOnaylandi()) {
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
        liste.setKesinOnaylandi(true);
        liste.setOnayTarihi(Instant.now());
        liste.setOnaylayanAdmin(adminUsername);
        kayitListesiRepository.save(liste);
        return toKayitResponse(donem, KayitListeFiltre.TUMU);
    }

    @Transactional
    public KayitListesiResponse geriAlKesinListe() {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        KayitListesi liste = currentListe(donem);
        if (!liste.isKesinOnaylandi()) {
            kesinListeService.resetComparison(donem);
            return toKayitResponse(donem, KayitListeFiltre.TUMU);
        }
        long atanan = basvuruRepository.countByStatusAndAtananBirimKoduIsNotNullAndBasvuruDonemiId(ApplicationStatus.APPROVED, donem.getId());
        if (atanan > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Birim dağıtımı yapıldıktan sonra kesin liste onayı geri alınamaz.");
        }
        liste.setKesinOnaylandi(false);
        liste.setOnayTarihi(null);
        liste.setOnaylayanAdmin(null);
        kayitListesiRepository.save(liste);
        kesinListeService.resetComparison(donem);
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

    private KayitListesi currentListe(BasvuruDonemi donem) {
        return kayitListesiRepository.findByBasvuruDonemiId(donem.getId()).orElseGet(() -> {
            KayitListesi created = new KayitListesi();
            created.setBasvuruDonemi(donem);
            created.setKesinOnaylandi(false);
            return kayitListesiRepository.save(created);
        });
    }

    private KayitListesiResponse toKayitResponse(BasvuruDonemi donem, KayitListeFiltre filtre) {
        KayitListesi liste = kayitListesiRepository.findByBasvuruDonemiId(donem.getId()).orElse(null);
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
                liste == null ? null : liste.getKesinListeYuklemeTarihi(),
                liste == null ? null : liste.getKesinListeYukleyenAdmin(),
                approved.size(),
                kesin,
                kesinListedeDegil,
                bekleyen,
                listedeEslesmeyenler.size(),
                liste != null && liste.isKesinOnaylandi(),
                liste == null ? null : liste.getOnayTarihi(),
                liste == null ? null : liste.getOnaylayanAdmin(),
                liste != null && liste.isKesinOnaylandi(),
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
