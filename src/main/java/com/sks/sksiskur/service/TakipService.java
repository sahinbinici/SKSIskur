package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.PuantajDurum;
import com.sks.sksiskur.domain.TakipDonem;
import com.sks.sksiskur.domain.TakipGun;
import com.sks.sksiskur.domain.TakipKapaliGun;
import com.sks.sksiskur.domain.TakipGirisPencere;
import com.sks.sksiskur.domain.TakipCetvelTuru;
import com.sks.sksiskur.domain.TakipStatus;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.TakipDonemRepository;
import com.sks.sksiskur.repository.TakipGirisPencereRepository;
import com.sks.sksiskur.repository.TakipKapaliGunRepository;
import com.sks.sksiskur.takip.WorkScheduleRules;
import com.sks.sksiskur.web.dto.AdminTakipOzetResponse;
import com.sks.sksiskur.web.dto.BirimAyGonderResponse;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.BirimOgrenciResponse;
import com.sks.sksiskur.web.dto.EkuantKaydetRequest;
import com.sks.sksiskur.web.dto.IzinRaporOgrenciResponse;
import com.sks.sksiskur.web.dto.PuantajKaydetRequest;
import com.sks.sksiskur.web.dto.OgrenciCalismaOzetResponse;
import com.sks.sksiskur.web.dto.TakipDonemResponse;
import com.sks.sksiskur.web.dto.TakipAySecim;
import com.sks.sksiskur.web.dto.TakipGirisAyOzet;
import com.sks.sksiskur.web.dto.TakipGirisPencereKaydetRequest;
import com.sks.sksiskur.web.dto.TakipGirisPencereResponse;
import com.sks.sksiskur.web.dto.TakipKapaliGunKaydetRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class TakipService {

    private static final int MAX_IZIN_GUNU = 10;

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final int DONEM_PENCERE_YIL = 0;
    private static final int DONEM_PENCERE_AY = 0;

    private final BasvuruRepository basvuruRepository;
    private final TakipDonemRepository takipDonemRepository;
    private final TakipKapaliGunRepository takipKapaliGunRepository;
    private final TakipGirisPencereRepository takipGirisPencereRepository;
    private final FileStorageService fileStorageService;
    private final int daysPerWeek;
    private final BigDecimal gunlukSaat;
    private final BasvuruDonemiService basvuruDonemiService;
    private final String relaxedBirimKodlari;
    private final boolean relaxedGiris;

    public TakipService(
            BasvuruRepository basvuruRepository,
            TakipDonemRepository takipDonemRepository,
            TakipKapaliGunRepository takipKapaliGunRepository,
            TakipGirisPencereRepository takipGirisPencereRepository,
            FileStorageService fileStorageService,
            @Value("${app.takip.haftalik-gun:3}") int daysPerWeek,
            @Value("${app.takip.gunluk-saat:7.5}") BigDecimal gunlukSaat,
            BasvuruDonemiService basvuruDonemiService,
            @Value("${app.takip.relaxed-birim-kodlari:}") String relaxedBirimKodlari,
            @Value("${app.takip.relaxed-giris:false}") boolean relaxedGiris
    ) {
        this.basvuruRepository = basvuruRepository;
        this.takipDonemRepository = takipDonemRepository;
        this.takipKapaliGunRepository = takipKapaliGunRepository;
        this.takipGirisPencereRepository = takipGirisPencereRepository;
        this.fileStorageService = fileStorageService;
        this.daysPerWeek = daysPerWeek;
        this.gunlukSaat = gunlukSaat;
        this.basvuruDonemiService = basvuruDonemiService;
        this.relaxedBirimKodlari = relaxedBirimKodlari;
        this.relaxedGiris = relaxedGiris;
    }

    private boolean isRelaxedBirim(String birimKodu) {
        if (birimKodu == null || relaxedBirimKodlari == null || relaxedBirimKodlari.isBlank()) {
            return false;
        }
        return Arrays.stream(relaxedBirimKodlari.split(","))
                .map(String::trim)
                .anyMatch(kod -> kod.equals(birimKodu));
    }

    /** Geliştirmede yalnızca tarih penceresini açar; EK-6 onayı ve diğer kurallar durur. */
    private boolean windowsRelaxed() {
        return relaxedGiris;
    }

    private boolean windowOverride(String birimKodu) {
        return windowsRelaxed() || isRelaxedBirim(birimKodu);
    }

    @Transactional(readOnly = true)
    public List<BirimOgrenciResponse> listStudents(String birimKodu) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        return basvuruRepository
                .findByAtananBirimKoduAndStatusAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
                        birimKodu, ApplicationStatus.APPROVED, donem.getId())
                .stream()
                .filter(basvuru -> basvuru.isAssigned()
                        && (isRelaxedBirim(birimKodu) || basvuru.canWorkIn(YearMonth.now(ZONE))))
                .map(this::toOgrenci)
                .toList();
    }

    @Transactional(readOnly = true)
    public BirimAylikRaporResponse monthlyReport(String birimKodu, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        List<Basvuru> basvurular = assignedKesin(birimKodu, basvuruDonemiService.requireActive()).stream()
                .filter(basvuru -> isRelaxedBirim(birimKodu) || basvuru.canWorkIn(month))
                .toList();
        String birimAdi = basvurular.stream()
                .map(Basvuru::getAtananBirimAdi)
                .filter(ad -> ad != null && !ad.isBlank())
                .findFirst()
                .orElse("");
        return monthlyReportFor(basvurular, yil, ay, birimAdi, true);
    }

    @Transactional(readOnly = true)
    public BirimAylikRaporResponse adminMonthlyReport(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        BasvuruDonemi donem = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        return monthlyReportFor(
                assignedKesin(blankToNull(birimKodu), donem),
                yil,
                ay,
                reportBirimAdi(birimKodu, donem),
                false
        );
    }

    @Transactional(readOnly = true)
    public AdminTakipOzetResponse adminTakipOzet(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        List<Basvuru> students = assignedKesin(blankToNull(birimKodu), period);
        Map<Long, TakipDonem> donemler = loadDonemler(students, yil, ay);
        List<AdminTakipOzetResponse.Satir> rows = new ArrayList<>();
        for (Basvuru basvuru : students) {
            TakipDonem donem = donemler.get(basvuru.getId());
            int ekuant = 0;
            int geldi = 0;
            int gelmedi = 0;
            int izinli = 0;
            int raporlu = 0;
            TakipStatus status = null;
            TakipStatus ekuantStatus = null;
            TakipStatus puantajStatus = null;
            boolean locked = false;
            if (donem != null) {
                ekuant = donem.getGunler().size();
                status = donem.getStatus();
                ekuantStatus = donem.resolveEkuantStatus();
                puantajStatus = donem.resolvePuantajStatus();
                locked = donem.isEkuantLocked() || donem.isPuantajLocked();
                for (TakipGun gun : donem.getGunler()) {
                    if (gun.getDurum() == PuantajDurum.GELDI) geldi++;
                    else if (gun.getDurum() == PuantajDurum.GELMEDI) gelmedi++;
                    else if (gun.getDurum() == PuantajDurum.IZINLI) izinli++;
                    else if (gun.getDurum() == PuantajDurum.RAPORLU) raporlu++;
                }
            }
            rows.add(new AdminTakipOzetResponse.Satir(
                    basvuru.getId(),
                    basvuru.getStudent().getOgrenciNo(),
                    basvuru.getStudent().getTcKimlikNo(),
                    basvuru.getStudent().getAdSoyad(),
                    basvuru.getAtananBirimKodu(),
                    basvuru.getAtananBirimAdi(),
                    ekuant,
                    geldi,
                    gelmedi,
                    izinli,
                    raporlu,
                    status,
                    ekuantStatus,
                    puantajStatus,
                    locked,
                    basvuru.getIliskiBitisTarihi()
            ));
        }
        return new AdminTakipOzetResponse(yil, ay, blankToNull(birimKodu), reportBirimAdi(birimKodu, period), rows);
    }

    public record TerminatedStudentRow(
            String ogrenciNo,
            String tcKimlikNo,
            String adSoyad,
            String birimAdi,
            java.time.LocalDate iliskiBitisTarihi
    ) {
    }

    @Transactional(readOnly = true)
    public List<TerminatedStudentRow> terminatedStudents(Long basvuruDonemiId, String birimKodu) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        List<String> codes = unitCodes(birimKodu);
        return basvuruRepository.findByBasvuruDonemiIdAndIliskiBitisTarihiIsNotNullOrderByIliskiBitisTarihiAsc(period.getId()).stream()
                .filter(basvuru -> codes.isEmpty() || codes.contains(basvuru.getAtananBirimKodu()))
                .map(basvuru -> new TerminatedStudentRow(
                        basvuru.getStudent().getOgrenciNo(),
                        basvuru.getStudent().getTcKimlikNo(),
                        basvuru.getStudent().getAdSoyad(),
                        basvuru.getAtananBirimAdi(),
                        basvuru.getIliskiBitisTarihi()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] terminatedStudentsCsv(Long basvuruDonemiId, String birimKodu) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        StringBuilder csv = new StringBuilder("\uFEFFÖğrenci No;T.C. Kimlik;Ad Soyad;Birim;İlişki Kesilme Tarihi;Başvuru Dönemi\r\n");
        terminatedStudents(basvuruDonemiId, birimKodu).forEach(row -> csv.append(csvValue(row.ogrenciNo())).append(';')
                .append(csvValue(row.tcKimlikNo())).append(';')
                .append(csvValue(row.adSoyad())).append(';')
                .append(csvValue(row.birimAdi())).append(';')
                .append(row.iliskiBitisTarihi()).append(';')
                .append(csvValue(period.getAd())).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public List<IzinRaporOgrenciResponse> leaveAndReportStudents(
            Long basvuruDonemiId,
            String birimKodu,
            int yil,
            int ay,
            PuantajDurum durum
    ) {
        return leaveAndReportRecords(basvuruDonemiId, birimKodu, yil, ay, durum).stream()
                .map(record -> new IzinRaporOgrenciResponse(
                        record.basvuru().getId(), record.basvuru().getStudent().getOgrenciNo(),
                        record.basvuru().getStudent().getTcKimlikNo(),
                        record.basvuru().getStudent().getAdSoyad(), record.basvuru().getAtananBirimAdi(),
                        record.gun().getTarih(), record.gun().getDurum(), record.gun().getBelgeYolu() != null,
                        record.gun().getBelgeAdi()
                )).toList();
    }

    @Transactional(readOnly = true)
    public byte[] leaveAndReportCsv(Long basvuruDonemiId, String birimKodu, int yil, int ay, PuantajDurum durum) {
        StringBuilder csv = new StringBuilder("\uFEFFÖğrenci No;T.C. Kimlik;Ad Soyad;Birim;Tarih;Durum;Belge\r\n");
        leaveAndReportStudents(basvuruDonemiId, birimKodu, yil, ay, durum).forEach(row -> csv
                .append(csvValue(row.ogrenciNo())).append(';')
                .append(csvValue(row.tcKimlikNo())).append(';')
                .append(csvValue(row.adSoyad())).append(';')
                .append(csvValue(row.birimAdi())).append(';')
                .append(row.tarih()).append(';')
                .append(row.durum() == PuantajDurum.IZINLI ? "İzinli" : "Raporlu").append(';')
                .append(csvValue(row.belgeAdi())).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] leaveAndReportDocumentsZip(Long basvuruDonemiId, String birimKodu, int yil, int ay, PuantajDurum durum) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (LeaveAndReportRecord record : leaveAndReportRecords(basvuruDonemiId, birimKodu, yil, ay, durum)) {
                TakipGun gun = record.gun();
                if (gun.getBelgeYolu() == null || gun.getBelgeYolu().isBlank()) continue;
                Path path = fileStorageService.resolve(gun.getBelgeYolu());
                if (!Files.exists(path)) continue;
                String name = safeZipName(record.basvuru().getStudent().getOgrenciNo() + "_" + gun.getTarih() + "_"
                        + gun.getDurum() + "_" + (gun.getBelgeAdi() == null ? "belge" : gun.getBelgeAdi()));
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(path, zip);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "İzin ve rapor dosyaları hazırlanamadı.");
        }
    }

    @Transactional(readOnly = true)
    public List<LocalDate> adminKapaliGunler(Long basvuruDonemiId) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        return takipKapaliGunRepository.findByBasvuruDonemiIdOrderByTarihAsc(period.getId()).stream()
                .map(TakipKapaliGun::getTarih)
                .toList();
    }

    @Transactional
    public List<LocalDate> saveAdminKapaliGunler(Long basvuruDonemiId, TakipKapaliGunKaydetRequest request) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        if (!period.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Kapalı dönemlerde giriş günleri değiştirilemez.");
        }
        Set<LocalDate> days = request.gunler() == null ? Set.of() : new HashSet<>(request.gunler());
        takipKapaliGunRepository.deleteByBasvuruDonemiId(period.getId());
        List<TakipKapaliGun> closed = days.stream().sorted().map(date -> {
            TakipKapaliGun item = new TakipKapaliGun();
            item.setBasvuruDonemi(period);
            item.setTarih(date);
            return item;
        }).toList();
        takipKapaliGunRepository.saveAll(closed);
        return closed.stream().map(TakipKapaliGun::getTarih).toList();
    }

    @Transactional(readOnly = true)
    public TakipGirisPencereResponse adminGirisPencere(Long basvuruDonemiId, int yil, int ay) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        requireMonth(yil, ay);
        return toGirisPencere(period.getId(), yil, ay, false);
    }

    @Transactional(readOnly = true)
    public java.util.List<TakipGirisAyOzet> adminGirisPencereleri(Long basvuruDonemiId) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        return takipGirisPencereRepository.findByBasvuruDonemiIdAndYilGreaterThanOrderByAyAsc(period.getId(), 0).stream()
                .map(window -> new TakipGirisAyOzet(
                        window.getYil(),
                        window.getAy(),
                        window.getEkuantBaslangic(),
                        window.getEkuantBitis(),
                        window.getPuantajBaslangic(),
                        window.getPuantajBitis()
                ))
                .toList();
    }

    @Transactional
    public TakipGirisPencereResponse saveAdminGirisPencere(
            Long basvuruDonemiId,
            int yil,
            int ay,
            TakipGirisPencereKaydetRequest request
    ) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        if (!period.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Kapalı dönemlerde giriş tarihleri değiştirilemez.");
        }
        requireMonth(yil, ay);
        LocalDate ekuantStart = request.ekuantBaslangic();
        LocalDate ekuantEnd = request.ekuantBitis();
        LocalDate puantajStart = request.puantajBaslangic();
        LocalDate puantajEnd = request.puantajBitis();
        assertRange("EK-6", ekuantStart, ekuantEnd);
        assertRange("Puantaj", puantajStart, puantajEnd);
        List<TakipAySecim> aylar = request.aylar() == null || request.aylar().isEmpty()
                ? List.of(new TakipAySecim(yil, ay))
                : request.aylar();
        for (TakipAySecim secim : aylar) {
            requireMonth(secim.yil(), secim.ay());
            TakipGirisPencere window = takipGirisPencereRepository
                    .findByBasvuruDonemiIdAndYilAndAy(period.getId(), secim.yil(), secim.ay())
                    .orElseGet(() -> {
                        TakipGirisPencere created = new TakipGirisPencere();
                        created.setBasvuruDonemi(period);
                        created.setYil(secim.yil());
                        created.setAy(secim.ay());
                        return created;
                    });
            window.setEkuantBaslangic(ekuantStart);
            window.setEkuantBitis(ekuantEnd);
            window.setPuantajBaslangic(puantajStart);
            window.setPuantajBitis(puantajEnd);
            takipGirisPencereRepository.save(window);
        }
        return toGirisPencere(period.getId(), yil, ay, false);
    }

    private void assertRange(String label, LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " için başlangıç ve bitiş tarihi birlikte girilmelidir.");
        }
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " bitiş tarihi başlangıçtan önce olamaz.");
        }
    }

    @Transactional(readOnly = true)
    public TakipDonemResponse adminGet(Long basvuruDonemiId, Long basvuruId, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        Basvuru basvuru = basvuruRepository.findDetailedById(basvuruId)
                .filter(item -> item.isAssigned() && item.getBasvuruDonemi() != null && item.getBasvuruDonemi().getId().equals(period.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Birime atanmış öğrenci bulunamadı."));
        return takipDonemRepository.findByBasvuruIdAndYilAndAy(basvuruId, yil, ay)
                .map(donem -> toResponse(donem, month))
                .orElseGet(() -> emptyDraft(basvuru, month));
    }

    private BirimAylikRaporResponse monthlyReportFor(
            List<Basvuru> basvurular,
            int yil,
            int ay,
            String birimAdi,
            boolean birimView
    ) {
        requireMonth(yil, ay);
        Map<Long, TakipDonem> donemler = loadDonemler(basvurular, yil, ay);
        int onayli = 0;
        int onayBekleyen = 0;
        int gonderilmeyen = 0;
        int tamamlanmamis = 0;
        int ekuantHazir = 0;
        int ekuantKilitli = 0;
        int puantajHazir = 0;
        String birimKodu = basvurular.isEmpty() ? null : basvurular.getFirst().getAtananBirimKodu();
        YearMonth month = YearMonth.of(yil, ay);
        for (Basvuru basvuru : basvurular) {
            TakipDonem donem = donemler.get(basvuru.getId());
            TakipStatus ekuantStatus = donem == null ? TakipStatus.DRAFT : donem.resolveEkuantStatus();
            TakipStatus puantajStatus = donem == null ? TakipStatus.DRAFT : donem.resolvePuantajStatus();
            if (ekuantStatus == TakipStatus.SUBMITTED || puantajStatus == TakipStatus.SUBMITTED) {
                onayBekleyen++;
            }
            if (puantajStatus == TakipStatus.APPROVED) {
                onayli++;
            }
            if (ekuantStatus == TakipStatus.DRAFT && puantajStatus == TakipStatus.DRAFT) {
                gonderilmeyen++;
            }
            boolean ekuantLocked = donem != null && donem.isEkuantLocked();
            boolean puantajLocked = donem != null && donem.isPuantajLocked();
            if (ekuantLocked) {
                ekuantKilitli++;
            } else if (readyToSubmitEkuant(donem, month, birimKodu)) {
                ekuantHazir++;
            } else {
                tamamlanmamis++;
            }
            if (!puantajLocked && ekuantStatus == TakipStatus.APPROVED && readyToSubmitPuantaj(donem, month, birimKodu)) {
                puantajHazir++;
            }
        }
        Long periodId = basvurular.isEmpty() || basvurular.getFirst().getBasvuruDonemi() == null
                ? null : basvurular.getFirst().getBasvuruDonemi().getId();
        TakipGirisPencereResponse window = periodId == null
                ? emptyGirisPencere(yil, ay, windowOverride(birimKodu))
                : toGirisPencere(periodId, yil, ay, windowOverride(birimKodu));
        boolean ekuantGonderilebilir = !basvurular.isEmpty()
                && ekuantHazir == basvurular.size()
                && window.ekuantAcik();
        boolean puantajGonderilebilir = !basvurular.isEmpty()
                && puantajHazir == basvurular.size()
                && ekuantKilitli == basvurular.size()
                && window.puantajAcik();
        boolean yazdirilabilir = !basvurular.isEmpty() && onayli == basvurular.size();
        boolean gonderilebilir = ekuantGonderilebilir || puantajGonderilebilir;
        List<BirimAylikRaporResponse.RaporOgrenci> rows = new ArrayList<>();
        int sira = 1;
        for (Basvuru basvuru : basvurular) {
            TakipDonem donem = donemler.get(basvuru.getId());
            List<LocalDate> ekuant = donem == null ? List.of() : donem.getGunler().stream()
                    .map(TakipGun::getTarih)
                    .sorted()
                    .toList();
            List<LocalDate> geldi = datesWithStatus(donem, PuantajDurum.GELDI);
            List<LocalDate> gelmedi = datesWithStatus(donem, PuantajDurum.GELMEDI);
            List<LocalDate> izinli = datesWithStatus(donem, PuantajDurum.IZINLI);
            List<LocalDate> raporlu = datesWithStatus(donem, PuantajDurum.RAPORLU);
            BigDecimal toplamSaat = gunlukSaat.multiply(BigDecimal.valueOf(geldi.size()))
                    .setScale(1, RoundingMode.HALF_UP);
            rows.add(new BirimAylikRaporResponse.RaporOgrenci(
                    sira++,
                    basvuru.getId(),
                    basvuru.getStudent().getOgrenciNo(),
                    basvuru.getStudent().getTcKimlikNo(),
                    basvuru.getStudent().getAd(),
                    basvuru.getStudent().getSoyad(),
                    basvuru.getIban(),
                    ekuant,
                    geldi,
                    gelmedi,
                    izinli,
                    raporlu,
                    geldi.size(),
                    toplamSaat,
                    donem == null ? null : donem.getStatus(),
                    donem == null ? null : donem.resolveEkuantStatus(),
                    donem == null ? null : donem.resolvePuantajStatus()
            ));
        }
        return new BirimAylikRaporResponse(
                yil,
                ay,
                birimAdi,
                gunlukSaat.setScale(1, RoundingMode.HALF_UP),
                birimView ? yazdirilabilir : true,
                birimView && gonderilebilir,
                birimView && ekuantGonderilebilir,
                birimView && puantajGonderilebilir,
                onayli,
                onayBekleyen,
                gonderilmeyen,
                birimView ? tamamlanmamis : 0,
                window.ekuantAcik(),
                window.puantajAcik(),
                window.ekuantBaslangic(),
                window.ekuantBitis(),
                window.puantajBaslangic(),
                window.puantajBitis(),
                rows
        );
    }

    private List<LocalDate> datesWithStatus(TakipDonem donem, PuantajDurum durum) {
        if (donem == null) {
            return List.of();
        }
        return donem.getGunler().stream()
                .filter(gun -> gun.getDurum() == durum)
                .map(TakipGun::getTarih)
                .sorted()
                .toList();
    }

    private Map<Long, TakipDonem> loadDonemler(List<Basvuru> students, int yil, int ay) {
        List<Long> ids = students.stream().map(Basvuru::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return takipDonemRepository.findByYilAndAyAndBasvuruIdIn(yil, ay, ids).stream()
                .collect(Collectors.toMap(donem -> donem.getBasvuru().getId(), Function.identity()));
    }

    private List<Basvuru> assignedKesin(String birimKodu, BasvuruDonemi donem) {
        List<Basvuru> assigned = basvuruRepository
                .findByAtananBirimKoduIsNotNullAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(donem.getId());
        List<String> codes = unitCodes(birimKodu);
        if (codes.isEmpty()) {
            return assigned;
        }
        if (codes.size() == 1) {
            return basvuruRepository.findByAtananBirimKoduAndStatusAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
                    codes.getFirst(), ApplicationStatus.APPROVED, donem.getId());
        }
        return assigned.stream()
                .filter(basvuru -> codes.contains(basvuru.getAtananBirimKodu()))
                .toList();
    }

    private String reportBirimAdi(String birimKodu, BasvuruDonemi donem) {
        List<String> codes = unitCodes(birimKodu);
        if (codes.isEmpty()) {
            return "Tüm birimler";
        }
        if (codes.size() > 1) {
            return codes.size() + " birim";
        }
        return assignedKesin(birimKodu, donem).stream()
                .map(Basvuru::getAtananBirimAdi)
                .filter(ad -> ad != null && !ad.isBlank())
                .findFirst()
                .orElse(codes.getFirst());
    }

    private List<String> unitCodes(String birimKodu) {
        if (birimKodu == null || birimKodu.isBlank()) {
            return List.of();
        }
        return Arrays.stream(birimKodu.split("[,;]"))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String csvValue(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private List<LeaveAndReportRecord> leaveAndReportRecords(
            Long basvuruDonemiId,
            String birimKodu,
            int yil,
            int ay,
            PuantajDurum durum
    ) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        YearMonth month = requireMonth(yil, ay);
        Map<Long, TakipDonem> donemler = loadDonemler(assignedKesin(blankToNull(birimKodu), period), yil, ay);
        return donemler.values().stream()
                .flatMap(donem -> donem.getGunler().stream().map(gun -> new LeaveAndReportRecord(donem.getBasvuru(), gun)))
                .filter(record -> YearMonth.from(record.gun().getTarih()).equals(month))
                .filter(record -> record.gun().getDurum() == PuantajDurum.IZINLI || record.gun().getDurum() == PuantajDurum.RAPORLU)
                .filter(record -> durum == null || record.gun().getDurum() == durum)
                .sorted(Comparator.comparing((LeaveAndReportRecord record) -> record.gun().getTarih())
                        .thenComparing(record -> record.basvuru().getStudent().getAdSoyad()))
                .toList();
    }

    private String safeZipName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record LeaveAndReportRecord(Basvuru basvuru, TakipGun gun) { }

    @Transactional(readOnly = true)
    public TakipDonemResponse getOrCreate(String birimKodu, Long basvuruId, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        Basvuru basvuru = requireAssigned(birimKodu, basvuruId, month);
        return takipDonemRepository.findByBasvuruIdAndYilAndAy(basvuruId, yil, ay)
                .map(donem -> toResponse(donem, month))
                .orElseGet(() -> emptyDraft(basvuru, month));
    }

    @Transactional
    public TakipDonemResponse saveEkuant(String birimKodu, Long basvuruId, EkuantKaydetRequest request) {
        YearMonth month = requireMonth(request.yil(), request.ay());
        boolean relaxed = isRelaxedBirim(birimKodu);
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, request.yil(), request.ay());
        assertEkuantEditable(donem);
        Set<LocalDate> days = request.gunler() == null ? Set.of() : new HashSet<>(request.gunler());
        for (LocalDate date : days) {
            if (!YearMonth.from(date).equals(month)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, date + " seçilen aya ait değil.");
            }
        }
        Map<LocalDate, TakipGun> existing = donem.getGunler().stream()
                .collect(Collectors.toMap(TakipGun::getTarih, Function.identity()));
        if (!relaxed) {
            assertMonthlyQuotaNotExceeded(donem.getBasvuru(), month, existing.keySet(), days);
            assertWeeklyLimitNotExceeded(month, days);
            assertClosedDaysUnchanged(donem.getBasvuru(), month, existing.keySet(), days);
        }
        for (TakipGun gun : new ArrayList<>(donem.getGunler())) {
            if (!days.contains(gun.getTarih())) {
                fileStorageService.deleteQuietly(gun.getBelgeYolu());
                donem.getGunler().remove(gun);
            }
        }
        for (LocalDate date : days) {
            if (!existing.containsKey(date)) {
                TakipGun gun = new TakipGun();
                gun.setDonem(donem);
                gun.setTarih(date);
                donem.getGunler().add(gun);
            }
        }
        return toResponse(takipDonemRepository.save(donem), month);
    }

    @Transactional
    public TakipDonemResponse savePuantaj(String birimKodu, Long basvuruId, PuantajKaydetRequest request) {
        YearMonth month = requireMonth(request.yil(), request.ay());
        boolean relaxed = isRelaxedBirim(birimKodu);
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, request.yil(), request.ay());
        assertPuantajEditable(donem, relaxed);
        Map<LocalDate, TakipGun> byDate = donem.getGunler().stream()
                .collect(Collectors.toMap(TakipGun::getTarih, Function.identity()));
        if (request.kayitlar() != null) {
            if (!relaxed) {
                assertPermissionLimit(basvuruId, byDate, request.kayitlar());
            }
            LocalDate gelmediKesim = firstGelmediDate(donem, request.kayitlar());
            for (PuantajKaydetRequest.Kayit kayit : request.kayitlar()) {
                TakipGun gun = byDate.get(kayit.tarih());
                if (gun == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, kayit.tarih() + " EK-6 günü değil.");
                }
                if (!relaxed) {
                    assertEntryOpen(donem.getBasvuru(), kayit.tarih());
                    assertPuantajDate(kayit.tarih());
                    if (gelmediKesim != null && kayit.tarih().isAfter(gelmediKesim)) {
                        throw new ApiException(HttpStatus.CONFLICT,
                                "Mazeretsiz gelmediği girilen günden sonraki puantaj kilitlidir.");
                    }
                }
                PuantajDurum onceki = gun.getDurum();
                gun.setDurum(kayit.durum());
                if (kayit.durum() != PuantajDurum.IZINLI && kayit.durum() != PuantajDurum.RAPORLU
                        && (onceki == PuantajDurum.IZINLI || onceki == PuantajDurum.RAPORLU)) {
                    fileStorageService.deleteQuietly(gun.getBelgeYolu());
                    gun.setBelgeYolu(null);
                    gun.setBelgeAdi(null);
                    gun.setIcerikTipi(null);
                }
            }
        }
        applyGelmediCutoff(donem, month);
        return toResponse(takipDonemRepository.save(donem), month);
    }

    @Transactional
    public TakipDonemResponse uploadBelge(
            String birimKodu,
            Long basvuruId,
            int yil,
            int ay,
            LocalDate tarih,
            MultipartFile file
    ) {
        YearMonth month = requireMonth(yil, ay);
        boolean relaxed = isRelaxedBirim(birimKodu);
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, yil, ay);
        assertPuantajEditable(donem, relaxed);
        if (!relaxed) {
            assertEntryOpen(donem.getBasvuru(), tarih);
            assertNotAfterGelmedi(donem, tarih);
        }
        TakipGun gun = donem.getGunler().stream()
                .filter(item -> item.getTarih().equals(tarih))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Önce EK-6 günü seçilmelidir."));
        if (!relaxed) {
            assertPuantajDate(tarih);
        }
        if (gun.getDurum() != PuantajDurum.IZINLI && gun.getDurum() != PuantajDurum.RAPORLU) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Belge yalnızca izinli veya raporlu gün için yüklenir.");
        }
        fileStorageService.deleteQuietly(gun.getBelgeYolu());
        FileStorageService.StoredFile stored = fileStorageService.store(
                "takip-" + basvuruId + "-" + yil + "-" + ay,
                gun.getDurum().name(),
                file
        );
        gun.setBelgeAdi(stored.originalName());
        gun.setBelgeYolu(stored.relativePath());
        gun.setIcerikTipi(stored.contentType());
        return toResponse(takipDonemRepository.save(donem), month);
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadBelge(String birimKodu, Long basvuruId, int yil, int ay, LocalDate tarih) {
        requireAssigned(birimKodu, basvuruId, requireMonth(yil, ay));
        return belgeFromDonem(basvuruId, yil, ay, tarih);
    }

    @Transactional(readOnly = true)
    public DocumentDownload adminDownloadBelge(Long basvuruId, int yil, int ay, LocalDate tarih) {
        basvuruRepository.findDetailedById(basvuruId)
                .filter(Basvuru::isAssigned)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Birime atanmış öğrenci bulunamadı."));
        return belgeFromDonem(basvuruId, yil, ay, tarih);
    }

    private DocumentDownload belgeFromDonem(Long basvuruId, int yil, int ay, LocalDate tarih) {
        TakipDonem donem = takipDonemRepository.findByBasvuruIdAndYilAndAy(basvuruId, yil, ay)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Takip kaydı bulunamadı."));
        TakipGun gun = donem.getGunler().stream()
                .filter(item -> item.getTarih().equals(tarih))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Gün bulunamadı."));
        if (gun.getBelgeYolu() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Bu gün için belge yok.");
        }
        Path path = fileStorageService.resolve(gun.getBelgeYolu());
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Dosya diskte bulunamadı.");
        }
        return new DocumentDownload(
                new FileSystemResource(path),
                gun.getBelgeAdi() != null ? gun.getBelgeAdi() : "belge",
                gun.getIcerikTipi() != null ? gun.getIcerikTipi() : "application/octet-stream"
        );
    }

    @Transactional
    public BirimAyGonderResponse submitBirimAy(String birimKodu, int yil, int ay, TakipCetvelTuru cetvel) {
        if (cetvel == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Gönderilecek cetvel (EK-6 veya puantaj) belirtilmelidir.");
        }
        YearMonth month = requireMonth(yil, ay);
        List<Basvuru> basvurular = assignedKesin(birimKodu, basvuruDonemiService.requireActive()).stream()
                .filter(basvuru -> isRelaxedBirim(birimKodu) || basvuru.canWorkIn(month))
                .toList();
        if (basvurular.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bu ay için gönderilecek öğrenci yok.");
        }
        Map<Long, TakipDonem> donemler = loadDonemler(basvurular, yil, ay);
        for (Basvuru basvuru : basvurular) {
            TakipDonem donem = donemler.get(basvuru.getId());
            if (cetvel == TakipCetvelTuru.EKUANT && donem != null && donem.isEkuantLocked()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Bu ayın EK-6 kaydı gönderilmiş veya onaylı. Yeniden göndermek için SKS yöneticisinin iade etmesi gerekir.");
            }
            if (cetvel == TakipCetvelTuru.PUANTAJ && donem != null && donem.isPuantajLocked()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Bu ayın puantajı gönderilmiş veya onaylı. Yeniden göndermek için SKS yöneticisinin iade etmesi gerekir.");
            }
        }
        List<String> sorunlar = new ArrayList<>();
        for (Basvuru basvuru : basvurular) {
            TakipDonem donem = donemler.get(basvuru.getId());
            boolean ready = cetvel == TakipCetvelTuru.EKUANT
                    ? readyToSubmitEkuant(donem, month, birimKodu)
                    : readyToSubmitPuantaj(donem, month, birimKodu);
            if (!ready) {
                sorunlar.add(basvuru.getStudent().getAdSoyad());
            }
        }
        if (!sorunlar.isEmpty()) {
            String ne = cetvel == TakipCetvelTuru.EKUANT ? "EK-6" : "puantaj";
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Tüm öğrencilerin " + ne + " kaydı tamamlanmadan ay gönderilemez. Eksik veya hatalı: "
                            + String.join(", ", sorunlar));
        }
        int count = 0;
        for (Basvuru basvuru : basvurular) {
            TakipDonem donem = getOrCreateEntity(birimKodu, basvuru.getId(), yil, ay);
            finalizeSubmit(donem, month, birimKodu, cetvel);
            takipDonemRepository.save(donem);
            count++;
        }
        return new BirimAyGonderResponse(yil, ay, count);
    }

    @Transactional
    public TakipDonemResponse submit(String birimKodu, Long basvuruId, int yil, int ay, TakipCetvelTuru cetvel) {
        if (cetvel == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Gönderilecek cetvel (EK-6 veya puantaj) belirtilmelidir.");
        }
        YearMonth month = requireMonth(yil, ay);
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, yil, ay);
        finalizeSubmit(donem, month, birimKodu, cetvel);
        return toResponse(takipDonemRepository.save(donem), month);
    }

    private boolean readyToSubmitEkuant(TakipDonem donem, YearMonth month, String birimKodu) {
        try {
            assertReadyToSubmitEkuant(donem, month, birimKodu);
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }

    private boolean readyToSubmitPuantaj(TakipDonem donem, YearMonth month, String birimKodu) {
        try {
            assertReadyToSubmitPuantaj(donem, month, birimKodu);
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }

    private void assertReadyToSubmitEkuant(TakipDonem donem, YearMonth month, String birimKodu) {
        boolean relaxed = isRelaxedBirim(birimKodu);
        LocalDate today = LocalDate.now(ZONE);
        if (donem == null || donem.getGunler().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Göndermeden önce EK-6 günleri seçilmelidir.");
        }
        if (donem.isEkuantLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "Gönderilen EK-6 değiştirilemez.");
        }
        if (!windowOverride(birimKodu)) {
            assertEkuantWindowOpen(donem.getBasvuru(), month);
        }
        if (!relaxed) {
            if (month.isAfter(YearMonth.from(today))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Gelecek ay gönderilemez.");
            }
            Set<LocalDate> quotaDays = quotaDays(donem.getBasvuru(), month);
            List<String> ekuantErrors = WorkScheduleRules.validateMonthlyQuota(month, quotaDays, daysPerWeek, today);
            if (!ekuantErrors.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ekuantErrors.getFirst());
            }
            List<String> weeklyErrors = WorkScheduleRules.validateEkuant(
                    month, actualMonthDays(donem.getBasvuru(), month), daysPerWeek, today);
            if (!weeklyErrors.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, weeklyErrors.getFirst());
            }
        }
    }

    private void assertReadyToSubmitPuantaj(TakipDonem donem, YearMonth month, String birimKodu) {
        boolean relaxed = isRelaxedBirim(birimKodu);
        LocalDate today = LocalDate.now(ZONE);
        if (donem == null || donem.getGunler().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Göndermeden önce EK-6 günleri seçilmelidir.");
        }
        if (!relaxed && donem.resolveEkuantStatus() != TakipStatus.APPROVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Puantaj, EK-6 SKS tarafından onaylandıktan sonra gönderilir.");
        }
        if (donem.isPuantajLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "Gönderilen puantaj değiştirilemez.");
        }
        if (!windowOverride(birimKodu)) {
            assertPuantajWindowOpen(donem.getBasvuru(), month);
        }
        LocalDate gelmediKesim = firstGelmediDate(donem, List.of());
        for (TakipGun gun : donem.getGunler()) {
            if (gelmediKesim != null && gun.getTarih().isAfter(gelmediKesim)) {
                continue;
            }
            if (!relaxed && gun.getTarih().isAfter(today)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Gelecek günler için puantaj girilemez. " + gun.getTarih()
                                + " tarihini EK-6'dan çıkarın veya ay ilerledikçe işaretleyin.");
            }
            if (gun.getDurum() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, gun.getTarih() + " için puantaj girilmedi.");
            }
            if (!relaxed
                    && (gun.getDurum() == PuantajDurum.IZINLI || gun.getDurum() == PuantajDurum.RAPORLU)
                    && (gun.getBelgeYolu() == null || gun.getBelgeYolu().isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        gun.getTarih() + " izinli/raporlu olduğu için dilekçe veya rapor yüklenmelidir.");
            }
        }
    }

    private void finalizeSubmit(TakipDonem donem, YearMonth month, String birimKodu, TakipCetvelTuru cetvel) {
        Instant now = Instant.now();
        if (cetvel == TakipCetvelTuru.EKUANT) {
            assertReadyToSubmitEkuant(donem, month, birimKodu);
            donem.setEkuantStatus(TakipStatus.SUBMITTED);
            donem.setEkuantGonderimTarihi(now);
            donem.setEkuantOnayTarihi(null);
        } else {
            assertReadyToSubmitPuantaj(donem, month, birimKodu);
            donem.setPuantajStatus(TakipStatus.SUBMITTED);
            donem.setPuantajGonderimTarihi(now);
            donem.setPuantajOnayTarihi(null);
        }
        donem.setGonderimTarihi(now);
        donem.setOnayTarihi(null);
        donem.syncOverallStatus();
    }

    @Transactional
    public TakipDonemResponse adminApprove(Long basvuruDonemiId, Long basvuruId, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        Basvuru basvuru = basvuruRepository.findDetailedById(basvuruId)
                .filter(item -> item.isAssigned()
                        && item.getBasvuruDonemi() != null
                        && item.getBasvuruDonemi().getId().equals(period.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Birime atanmış öğrenci bulunamadı."));
        TakipDonem donem = takipDonemRepository.findByBasvuruIdAndYilAndAy(basvuruId, yil, ay)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Öğrenci bu ay için kayıt göndermedi."));
        Instant now = Instant.now();
        if (donem.resolvePuantajStatus() == TakipStatus.SUBMITTED) {
            donem.setPuantajStatus(TakipStatus.APPROVED);
            donem.setPuantajOnayTarihi(now);
        } else if (donem.resolveEkuantStatus() == TakipStatus.SUBMITTED) {
            donem.setEkuantStatus(TakipStatus.APPROVED);
            donem.setEkuantOnayTarihi(now);
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca gönderilmiş EK-6 veya puantaj kayıtları onaylanabilir.");
        }
        donem.setOnayTarihi(now);
        donem.syncOverallStatus();
        return toResponse(takipDonemRepository.save(donem), month);
    }

    @Transactional
    public int adminApproveSubmitted(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        List<Basvuru> students = assignedKesin(birimKodu, period).stream()
                .filter(basvuru -> basvuru.canWorkIn(month))
                .toList();
        Map<Long, TakipDonem> donemler = loadDonemler(students, yil, ay);
        Instant now = Instant.now();
        int count = 0;
        for (Basvuru basvuru : students) {
            TakipDonem donem = donemler.get(basvuru.getId());
            if (donem == null) {
                continue;
            }
            boolean approved = false;
            if (donem.resolvePuantajStatus() == TakipStatus.SUBMITTED) {
                donem.setPuantajStatus(TakipStatus.APPROVED);
                donem.setPuantajOnayTarihi(now);
                approved = true;
            } else if (donem.resolveEkuantStatus() == TakipStatus.SUBMITTED) {
                donem.setEkuantStatus(TakipStatus.APPROVED);
                donem.setEkuantOnayTarihi(now);
                approved = true;
            }
            if (approved) {
                donem.setOnayTarihi(now);
                donem.syncOverallStatus();
                count++;
            }
        }
        if (count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Onaylanacak gönderilmiş kayıt bulunamadı.");
        }
        takipDonemRepository.saveAll(donemler.values());
        return count;
    }

    @Transactional
    public TakipDonemResponse adminReturn(Long basvuruDonemiId, Long basvuruId, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        basvuruRepository.findDetailedById(basvuruId)
                .filter(item -> item.isAssigned()
                        && item.getBasvuruDonemi() != null
                        && item.getBasvuruDonemi().getId().equals(period.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Birime atanmış öğrenci bulunamadı."));
        TakipDonem donem = takipDonemRepository.findByBasvuruIdAndYilAndAy(basvuruId, yil, ay)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Öğrenci bu ay için kayıt göndermedi."));
        returnToDraft(donem);
        return toResponse(takipDonemRepository.save(donem), month);
    }

    @Transactional
    public int adminReturnSubmitted(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        List<Basvuru> students = assignedKesin(birimKodu, period).stream()
                .filter(basvuru -> basvuru.canWorkIn(month))
                .toList();
        Map<Long, TakipDonem> donemler = loadDonemler(students, yil, ay);
        int count = 0;
        for (Basvuru basvuru : students) {
            TakipDonem donem = donemler.get(basvuru.getId());
            if (donem != null && (donem.isEkuantLocked() || donem.isPuantajLocked())) {
                returnToDraft(donem);
                count++;
            }
        }
        if (count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "İade edilecek gönderilmiş veya onaylı kayıt bulunamadı.");
        }
        takipDonemRepository.saveAll(donemler.values());
        return count;
    }

    @Transactional
    public void adminTerminate(Long basvuruDonemiId, Long basvuruId, int yil, int ay) {
        int count = adminTerminateMany(basvuruDonemiId, List.of(basvuruId), yil, ay);
        if (count == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Öğrencinin ilişkisi bu ay için zaten kesilmiş.");
        }
    }

    @Transactional
    public int adminTerminateMany(Long basvuruDonemiId, List<Long> basvuruIds, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        if (basvuruIds == null || basvuruIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "İlişik kesilecek öğrenci seçin.");
        }
        Set<Long> wanted = new HashSet<>(basvuruIds);
        List<Basvuru> students = assignedKesin(null, period).stream()
                .filter(basvuru -> wanted.contains(basvuru.getId()))
                .toList();
        int count = 0;
        for (Basvuru basvuru : students) {
            if (terminateForMonth(basvuru, month)) {
                count++;
            }
        }
        if (count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "İlişik kesilecek aktif öğrenci bulunamadı.");
        }
        return count;
    }

    private boolean terminateForMonth(Basvuru basvuru, YearMonth month) {
        if (!basvuru.isAssigned()) {
            return false;
        }
        LocalDate cut = month.atDay(1);
        if (basvuru.getIliskiBitisTarihi() != null && !basvuru.getIliskiBitisTarihi().isAfter(cut)) {
            return false;
        }
        basvuru.setIliskiBitisTarihi(cut);
        basvuruRepository.save(basvuru);
        return true;
    }

    private TakipDonem getOrCreateEntity(String birimKodu, Long basvuruId, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        Basvuru basvuru = requireAssigned(birimKodu, basvuruId, month);
        return takipDonemRepository.findByBasvuruIdAndYilAndAy(basvuruId, yil, ay).orElseGet(() -> {
            TakipDonem created = new TakipDonem();
            created.setBasvuru(basvuru);
            created.setYil(yil);
            created.setAy(ay);
            created.setStatus(TakipStatus.DRAFT);
            created.setEkuantStatus(TakipStatus.DRAFT);
            created.setPuantajStatus(TakipStatus.DRAFT);
            return takipDonemRepository.save(created);
        });
    }

    private Basvuru requireAssigned(String birimKodu, Long basvuruId, YearMonth month) {
        return basvuruRepository.findDetailedByIdAndAtananBirimKoduAndBasvuruDonemiAktifTrue(basvuruId, birimKodu)
                .filter(basvuru -> basvuru.isAssigned()
                        && (isRelaxedBirim(birimKodu) || basvuru.canWorkIn(month)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bu birime atanmış öğrenci bulunamadı."));
    }

    private void assertEkuantEditable(TakipDonem donem) {
        if (donem.isEkuantLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "Gönderilen EK-6 değiştirilemez.");
        }
        if (!windowOverride(donem.getBasvuru().getAtananBirimKodu())) {
            assertEkuantWindowOpen(donem.getBasvuru(), YearMonth.of(donem.getYil(), donem.getAy()));
        }
    }

    private void assertPuantajEditable(TakipDonem donem, boolean relaxed) {
        if (donem.isPuantajLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "Gönderilen puantaj değiştirilemez.");
        }
        if (!relaxed && donem.resolveEkuantStatus() != TakipStatus.APPROVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Puantaj, gönderilen EK-6 SKS tarafından onaylandıktan sonra doldurulur.");
        }
        if (!windowOverride(donem.getBasvuru().getAtananBirimKodu())) {
            assertPuantajWindowOpen(donem.getBasvuru(), YearMonth.of(donem.getYil(), donem.getAy()));
        }
    }

    private void returnToDraft(TakipDonem donem) {
        if (donem.isPuantajLocked()) {
            donem.setPuantajStatus(TakipStatus.DRAFT);
            donem.setPuantajGonderimTarihi(null);
            donem.setPuantajOnayTarihi(null);
        } else if (donem.isEkuantLocked()) {
            donem.setEkuantStatus(TakipStatus.DRAFT);
            donem.setEkuantGonderimTarihi(null);
            donem.setEkuantOnayTarihi(null);
        } else {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Yalnızca gönderilmiş veya onaylanmış kayıtlar birime iade edilebilir.");
        }
        donem.setOnayTarihi(null);
        donem.syncOverallStatus();
        if (donem.getStatus() == TakipStatus.DRAFT) {
            donem.setGonderimTarihi(null);
        }
    }

    private LocalDate firstGelmediDate(TakipDonem donem, List<PuantajKaydetRequest.Kayit> kayitlar) {
        Map<LocalDate, PuantajDurum> statuses = new HashMap<>();
        for (TakipGun gun : donem.getGunler()) {
            statuses.put(gun.getTarih(), gun.getDurum());
        }
        if (kayitlar != null) {
            for (PuantajKaydetRequest.Kayit kayit : kayitlar) {
                statuses.put(kayit.tarih(), kayit.durum());
            }
        }
        return statuses.entrySet().stream()
                .filter(entry -> entry.getValue() == PuantajDurum.GELMEDI)
                .map(Map.Entry::getKey)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private void applyGelmediCutoff(TakipDonem donem, YearMonth month) {
        LocalDate cutoff = firstGelmediDate(donem, List.of());
        Basvuru basvuru = donem.getBasvuru();
        LocalDate expectedEnd = month.plusMonths(1).atDay(1);
        if (cutoff == null) {
            if (expectedEnd.equals(basvuru.getIliskiBitisTarihi())) {
                basvuru.setIliskiBitisTarihi(null);
            }
            return;
        }
        for (TakipGun gun : donem.getGunler()) {
            if (gun.getTarih().isAfter(cutoff)) {
                fileStorageService.deleteQuietly(gun.getBelgeYolu());
                gun.setDurum(null);
                gun.setBelgeYolu(null);
                gun.setBelgeAdi(null);
                gun.setIcerikTipi(null);
            }
        }
        if (basvuru.getIliskiBitisTarihi() == null) {
            basvuru.setIliskiBitisTarihi(expectedEnd);
        }
    }

    private void assertNotAfterGelmedi(TakipDonem donem, LocalDate tarih) {
        LocalDate cutoff = firstGelmediDate(donem, List.of());
        if (cutoff != null && tarih.isAfter(cutoff)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Mazeretsiz gelmediği girilen günden sonraki puantaj kilitlidir.");
        }
    }

    private void assertPuantajDate(LocalDate date) {
        LocalDate today = LocalDate.now(ZONE);
        if (date.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Gelecek günler için puantaj girilemez.");
        }
    }

    private YearMonth requireMonth(int yil, int ay) {
        try {
            return YearMonth.of(yil, ay);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçersiz yıl/ay.");
        }
    }

    private TakipDonemResponse emptyDraft(Basvuru basvuru, YearMonth month) {
        String birimKodu = basvuru.getAtananBirimKodu();
        boolean relaxed = isRelaxedBirim(birimKodu);
        TakipGirisPencereResponse window = toGirisPencere(
                basvuru.getBasvuruDonemi().getId(), month.getYear(), month.getMonthValue(),
                windowOverride(birimKodu));
        return new TakipDonemResponse(
                null,
                basvuru.getId(),
                month.getYear(),
                month.getMonthValue(),
                TakipStatus.DRAFT,
                TakipStatus.DRAFT,
                TakipStatus.DRAFT,
                false,
                false,
                false,
                null,
                List.of(),
                List.of(),
                relaxed ? List.of() : ekuantWarnings(basvuru, month),
                closedDays(basvuru.getBasvuruDonemi().getId(), month),
                quotaDays(basvuru, month).size(),
                WorkScheduleRules.monthlyQuota(daysPerWeek),
                countPermissionDays(basvuru.getId()),
                MAX_IZIN_GUNU,
                relaxed,
                null,
                window.ekuantBaslangic(),
                window.ekuantBitis(),
                window.ekuantAcik(),
                window.puantajBaslangic(),
                window.puantajBitis(),
                window.puantajAcik(),
                toOgrenci(basvuru)
        );
    }

    private TakipDonemResponse toResponse(TakipDonem donem, YearMonth month) {
        List<LocalDate> ekuant = donem.getGunler().stream()
                .map(TakipGun::getTarih)
                .sorted()
                .toList();
        List<TakipDonemResponse.PuantajGunResponse> puantaj = donem.getGunler().stream()
                .sorted(Comparator.comparing(TakipGun::getTarih))
                .map(gun -> new TakipDonemResponse.PuantajGunResponse(
                        gun.getTarih(),
                        gun.getDurum(),
                        gun.getBelgeAdi(),
                        gun.getBelgeYolu() != null && !gun.getBelgeYolu().isBlank()
                ))
                .toList();
        Set<LocalDate> quotaDays = quotaDays(donem.getBasvuru(), month);
        String birimKodu = donem.getBasvuru().getAtananBirimKodu();
        boolean relaxed = isRelaxedBirim(birimKodu);
        List<String> warnings = relaxed ? List.of() : ekuantWarnings(donem.getBasvuru(), month);
        TakipGirisPencereResponse window = toGirisPencere(
                donem.getBasvuru().getBasvuruDonemi().getId(), month.getYear(), month.getMonthValue(),
                windowOverride(birimKodu));
        return new TakipDonemResponse(
                donem.getId(),
                donem.getBasvuru().getId(),
                donem.getYil(),
                donem.getAy(),
                donem.getStatus(),
                donem.resolveEkuantStatus(),
                donem.resolvePuantajStatus(),
                donem.isLocked(),
                donem.isEkuantLocked(),
                donem.isPuantajLocked(),
                donem.getGonderimTarihi(),
                ekuant,
                puantaj,
                warnings,
                closedDays(donem.getBasvuru().getBasvuruDonemi().getId(), month),
                quotaDays.size(),
                WorkScheduleRules.monthlyQuota(daysPerWeek),
                countPermissionDays(donem.getBasvuru().getId()),
                MAX_IZIN_GUNU,
                relaxed,
                firstGelmediDate(donem, List.of()),
                window.ekuantBaslangic(),
                window.ekuantBitis(),
                window.ekuantAcik(),
                window.puantajBaslangic(),
                window.puantajBitis(),
                window.puantajAcik(),
                toOgrenci(donem.getBasvuru())
        );
    }

    private void assertMonthlyQuotaNotExceeded(Basvuru basvuru, YearMonth actualMonth,
                                               Set<LocalDate> existing, Set<LocalDate> proposed) {
        Set<YearMonth> affectedQuotaMonths = new HashSet<>();
        existing.forEach(date -> affectedQuotaMonths.add(WorkScheduleRules.quotaMonth(date)));
        proposed.forEach(date -> affectedQuotaMonths.add(WorkScheduleRules.quotaMonth(date)));

        for (YearMonth quotaMonth : affectedQuotaMonths) {
            List<String> errors = WorkScheduleRules.validateMonthlyQuota(
                    quotaMonth,
                    quotaDaysAfterChanges(basvuru, actualMonth, proposed, quotaMonth),
                    daysPerWeek
            );
            errors.stream()
                    .filter(error -> error.contains("en fazla"))
                    .findFirst()
                    .ifPresent(error -> {
                        throw new ApiException(HttpStatus.BAD_REQUEST, error);
                    });
        }
    }

    private void assertWeeklyLimitNotExceeded(YearMonth month, Set<LocalDate> proposed) {
        WorkScheduleRules.validateEkuant(month, proposed, daysPerWeek).stream()
                .filter(error -> error.contains("en fazla"))
                .findFirst()
                .ifPresent(error -> {
                    throw new ApiException(HttpStatus.BAD_REQUEST, error);
                });
    }

    private List<String> ekuantWarnings(Basvuru basvuru, YearMonth month) {
        LocalDate today = LocalDate.now(ZONE);
        List<String> warnings = new ArrayList<>(
                WorkScheduleRules.validateMonthlyQuota(month, quotaDays(basvuru, month), daysPerWeek, today));
        warnings.addAll(WorkScheduleRules.validateEkuant(
                month, actualMonthDays(basvuru, month), daysPerWeek, today));
        return warnings.stream().distinct().toList();
    }

    private Set<LocalDate> actualMonthDays(Basvuru basvuru, YearMonth month) {
        return takipDonemRepository.findByBasvuruId(basvuru.getId()).stream()
                .flatMap(donem -> donem.getGunler().stream())
                .map(TakipGun::getTarih)
                .filter(date -> YearMonth.from(date).equals(month))
                .collect(Collectors.toSet());
    }

    private Set<LocalDate> quotaDays(Basvuru basvuru, YearMonth quotaMonth) {
        return takipDonemRepository.findByBasvuruId(basvuru.getId()).stream()
                .flatMap(donem -> donem.getGunler().stream())
                .map(TakipGun::getTarih)
                .filter(date -> WorkScheduleRules.quotaMonth(date).equals(quotaMonth))
                .collect(Collectors.toSet());
    }

    private Set<LocalDate> quotaDaysAfterChanges(Basvuru basvuru, YearMonth actualMonth,
                                                  Set<LocalDate> proposed, YearMonth quotaMonth) {
        Set<LocalDate> allDays = takipDonemRepository.findByBasvuruId(basvuru.getId()).stream()
                .flatMap(donem -> donem.getGunler().stream())
                .map(TakipGun::getTarih)
                .filter(date -> !YearMonth.from(date).equals(actualMonth))
                .collect(Collectors.toSet());
        allDays.addAll(proposed);
        return allDays.stream()
                .filter(date -> WorkScheduleRules.quotaMonth(date).equals(quotaMonth))
                .collect(Collectors.toSet());
    }

    private void assertPermissionLimit(Long basvuruId, Map<LocalDate, TakipGun> currentMonthDays,
                                       List<PuantajKaydetRequest.Kayit> changes) {
        // EK-6 günü oluşturulduğunda puantaj durumu henüz null olur. Collectors.toMap
        // null değer kabul etmediği için ilk izin/rapor/gelmedi seçiminde 500 dönüyordu.
        Map<LocalDate, PuantajDurum> statuses = new HashMap<>();
        takipDonemRepository.findByBasvuruId(basvuruId).stream()
                .flatMap(donem -> donem.getGunler().stream())
                .forEach(gun -> statuses.put(gun.getTarih(), gun.getDurum()));
        for (PuantajKaydetRequest.Kayit change : changes) {
            if (!currentMonthDays.containsKey(change.tarih())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, change.tarih() + " EK-6 günü değil.");
            }
            assertPuantajDate(change.tarih());
            statuses.put(change.tarih(), change.durum());
        }
        long izinGunleri = statuses.values().stream().filter(PuantajDurum.IZINLI::equals).count();
        if (izinGunleri > MAX_IZIN_GUNU) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Öğrencinin izin hakkı dolmuştur. Dönem boyunca en fazla " + MAX_IZIN_GUNU
                            + " gün izin girilebilir; yeni izin günü eklenemez.");
        }
    }

    private int countPermissionDays(Long basvuruId) {
        return (int) takipDonemRepository.findByBasvuruId(basvuruId).stream()
                .flatMap(donem -> donem.getGunler().stream())
                .filter(gun -> gun.getDurum() == PuantajDurum.IZINLI)
                .count();
    }

    private List<LocalDate> closedDays(Long basvuruDonemiId, YearMonth month) {
        return takipKapaliGunRepository.findByBasvuruDonemiIdAndTarihBetweenOrderByTarihAsc(
                basvuruDonemiId, month.atDay(1), month.atEndOfMonth()).stream().map(TakipKapaliGun::getTarih).toList();
    }

    private void assertEntryOpen(Basvuru basvuru, LocalDate date) {
        if (closedDays(basvuru.getBasvuruDonemi().getId(), YearMonth.from(date)).contains(date)) {
            throw new ApiException(HttpStatus.CONFLICT, date + " için EK-6 ve puantaj girişi yönetici tarafından kapatıldı.");
        }
    }

    private void assertClosedDaysUnchanged(Basvuru basvuru, YearMonth month, Set<LocalDate> existing, Set<LocalDate> requested) {
        for (LocalDate date : closedDays(basvuru.getBasvuruDonemi().getId(), month)) {
            if (existing.contains(date) != requested.contains(date)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        date + " için EK-6 girişi yönetici tarafından kapatıldı.");
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<OgrenciCalismaOzetResponse> ogrenciCalismaOzet(String ogrenciNo) {
        return basvuruRepository.findByStudentOgrenciNoAndBasvuruDonemiAktifTrue(ogrenciNo)
                .filter(Basvuru::isAssigned)
                .filter(basvuru -> basvuru.getStatus() == ApplicationStatus.APPROVED)
                .flatMap(basvuru -> {
                    YearMonth month = YearMonth.now(ZONE);
                    if (!basvuru.canWorkIn(month)) {
                        return Optional.empty();
                    }
                    int kullanilanIzin = countPermissionDays(basvuru.getId());
                    TakipDonem donem = takipDonemRepository
                            .findByBasvuruIdAndYilAndAy(basvuru.getId(), month.getYear(), month.getMonthValue())
                            .orElse(null);
                    int buAyTamGun = 0;
                    int buAyEkuantGun = donem == null ? 0 : donem.getGunler().size();
                    TakipStatus status = null;
                    boolean locked = false;
                    if (donem != null) {
                        status = donem.getStatus();
                        locked = donem.isPuantajLocked();
                        for (TakipGun gun : donem.getGunler()) {
                            if (gun.getDurum() == PuantajDurum.GELDI) {
                                buAyTamGun++;
                            }
                        }
                    }
                    return Optional.of(new OgrenciCalismaOzetResponse(
                            basvuru.getId(),
                            basvuru.getAtananBirimAdi(),
                            basvuru.getAtananBirimKodu(),
                            basvuru.getBasvuruDonemi() == null ? null : basvuru.getBasvuruDonemi().getAd(),
                            month.getYear(),
                            month.getMonthValue(),
                            kullanilanIzin,
                            MAX_IZIN_GUNU,
                            Math.max(0, MAX_IZIN_GUNU - kullanilanIzin),
                            buAyTamGun,
                            buAyEkuantGun,
                            status,
                            locked
                    ));
                });
    }

    private void assertEkuantWindowOpen(Basvuru basvuru, YearMonth month) {
        TakipGirisPencereResponse window = toGirisPencere(
                basvuru.getBasvuruDonemi().getId(), month.getYear(), month.getMonthValue(),
                windowOverride(basvuru.getAtananBirimKodu()));
        if (window.ekuantAcik()) {
            return;
        }
        throw new ApiException(HttpStatus.CONFLICT, windowMessage(
                "EK-6", window.ekuantBaslangic(), window.ekuantBitis()));
    }

    private void assertPuantajWindowOpen(Basvuru basvuru, YearMonth month) {
        TakipGirisPencereResponse window = toGirisPencere(
                basvuru.getBasvuruDonemi().getId(), month.getYear(), month.getMonthValue(),
                windowOverride(basvuru.getAtananBirimKodu()));
        if (window.puantajAcik()) {
            return;
        }
        throw new ApiException(HttpStatus.CONFLICT, windowMessage(
                "Puantaj", window.puantajBaslangic(), window.puantajBitis()));
    }

    private String windowMessage(String label, LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return label + " girişi için SKS henüz tarih aralığı belirlemedi.";
        }
        return label + " girişi yalnızca " + start + " – " + end + " tarihleri arasında yapılabilir.";
    }

    private TakipGirisPencereResponse toGirisPencere(Long periodId, int yil, int ay, boolean relaxed) {
        TakipGirisPencere monthly = takipGirisPencereRepository
                .findByBasvuruDonemiIdAndYilAndAy(periodId, yil, ay)
                .orElse(null);
        TakipGirisPencere donemlik = takipGirisPencereRepository
                .findByBasvuruDonemiIdAndYilAndAy(periodId, DONEM_PENCERE_YIL, DONEM_PENCERE_AY)
                .orElse(null);
        LocalDate ayEkuantStart = monthly == null ? null : monthly.getEkuantBaslangic();
        LocalDate ayEkuantEnd = monthly == null ? null : monthly.getEkuantBitis();
        LocalDate ayPuantajStart = monthly == null ? null : monthly.getPuantajBaslangic();
        LocalDate ayPuantajEnd = monthly == null ? null : monthly.getPuantajBitis();
        LocalDate donemEkuantStart = donemlik == null ? null : donemlik.getEkuantBaslangic();
        LocalDate donemEkuantEnd = donemlik == null ? null : donemlik.getEkuantBitis();
        LocalDate donemPuantajStart = donemlik == null ? null : donemlik.getPuantajBaslangic();
        LocalDate donemPuantajEnd = donemlik == null ? null : donemlik.getPuantajBitis();
        boolean ekuantAylik = completeRange(ayEkuantStart, ayEkuantEnd);
        boolean puantajAylik = completeRange(ayPuantajStart, ayPuantajEnd);
        LocalDate ekuantStart = ekuantAylik ? ayEkuantStart : donemEkuantStart;
        LocalDate ekuantEnd = ekuantAylik ? ayEkuantEnd : donemEkuantEnd;
        LocalDate puantajStart = puantajAylik ? ayPuantajStart : donemPuantajStart;
        LocalDate puantajEnd = puantajAylik ? ayPuantajEnd : donemPuantajEnd;
        return new TakipGirisPencereResponse(
                yil,
                ay,
                ekuantStart,
                ekuantEnd,
                relaxed || windowOpen(ekuantStart, ekuantEnd),
                ekuantAylik ? "AYLIK" : completeRange(donemEkuantStart, donemEkuantEnd) ? "DONEMLIK" : "YOK",
                puantajStart,
                puantajEnd,
                relaxed || windowOpen(puantajStart, puantajEnd),
                puantajAylik ? "AYLIK" : completeRange(donemPuantajStart, donemPuantajEnd) ? "DONEMLIK" : "YOK",
                ayEkuantStart,
                ayEkuantEnd,
                ayPuantajStart,
                ayPuantajEnd,
                donemEkuantStart,
                donemEkuantEnd,
                donemPuantajStart,
                donemPuantajEnd
        );
    }

    private boolean completeRange(LocalDate start, LocalDate end) {
        return start != null && end != null;
    }

    private TakipGirisPencereResponse emptyGirisPencere(int yil, int ay, boolean relaxed) {
        return new TakipGirisPencereResponse(
                yil, ay, null, null, relaxed, "YOK", null, null, relaxed, "YOK",
                null, null, null, null, null, null, null, null
        );
    }

    private boolean windowOpen(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return false;
        }
        LocalDate today = LocalDate.now(ZONE);
        return !today.isBefore(start) && !today.isAfter(end);
    }

    private BirimOgrenciResponse toOgrenci(Basvuru basvuru) {
        int kullanilanIzin = countPermissionDays(basvuru.getId());
        return new BirimOgrenciResponse(
                basvuru.getId(),
                basvuru.getStudent().getOgrenciNo(),
                basvuru.getStudent().getTcKimlikNo(),
                basvuru.getStudent().getAdSoyad(),
                basvuru.getStudent().getFakulte(),
                basvuru.getStudent().getBolum(),
                basvuru.getStudent().getProgram(),
                basvuru.resolveIletisimEposta(),
                basvuru.resolveIletisimGsm(),
                kullanilanIzin,
                MAX_IZIN_GUNU,
                Math.max(0, MAX_IZIN_GUNU - kullanilanIzin)
        );
    }
}
