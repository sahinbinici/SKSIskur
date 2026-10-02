package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.PuantajDurum;
import com.sks.sksiskur.domain.TakipDonem;
import com.sks.sksiskur.domain.TakipGun;
import com.sks.sksiskur.domain.TakipKapaliGun;
import com.sks.sksiskur.domain.TakipStatus;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.TakipDonemRepository;
import com.sks.sksiskur.repository.TakipKapaliGunRepository;
import com.sks.sksiskur.takip.WorkScheduleRules;
import com.sks.sksiskur.web.dto.AdminTakipOzetResponse;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.BirimOgrenciResponse;
import com.sks.sksiskur.web.dto.EkuantKaydetRequest;
import com.sks.sksiskur.web.dto.IzinRaporOgrenciResponse;
import com.sks.sksiskur.web.dto.PuantajKaydetRequest;
import com.sks.sksiskur.web.dto.OgrenciCalismaOzetResponse;
import com.sks.sksiskur.web.dto.TakipDonemResponse;
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

    private final BasvuruRepository basvuruRepository;
    private final TakipDonemRepository takipDonemRepository;
    private final TakipKapaliGunRepository takipKapaliGunRepository;
    private final FileStorageService fileStorageService;
    private final int daysPerWeek;
    private final BigDecimal gunlukSaat;
    private final BasvuruDonemiService basvuruDonemiService;

    public TakipService(
            BasvuruRepository basvuruRepository,
            TakipDonemRepository takipDonemRepository,
            TakipKapaliGunRepository takipKapaliGunRepository,
            FileStorageService fileStorageService,
            @Value("${app.takip.haftalik-gun:3}") int daysPerWeek,
            @Value("${app.takip.gunluk-saat:7.5}") BigDecimal gunlukSaat,
            BasvuruDonemiService basvuruDonemiService
    ) {
        this.basvuruRepository = basvuruRepository;
        this.takipDonemRepository = takipDonemRepository;
        this.takipKapaliGunRepository = takipKapaliGunRepository;
        this.fileStorageService = fileStorageService;
        this.daysPerWeek = daysPerWeek;
        this.gunlukSaat = gunlukSaat;
        this.basvuruDonemiService = basvuruDonemiService;
    }

    @Transactional(readOnly = true)
    public List<BirimOgrenciResponse> listStudents(String birimKodu) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        return basvuruRepository
                .findByAtananBirimKoduAndStatusAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
                        birimKodu, ApplicationStatus.APPROVED, donem.getId())
                .stream()
                .filter(basvuru -> basvuru.isAssigned() && basvuru.canWorkIn(YearMonth.now(ZONE)))
                .map(this::toOgrenci)
                .toList();
    }

    @Transactional(readOnly = true)
    public BirimAylikRaporResponse monthlyReport(String birimKodu, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        List<Basvuru> basvurular = assignedKesin(birimKodu, basvuruDonemiService.requireActive()).stream()
                .filter(basvuru -> basvuru.canWorkIn(month))
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
            boolean locked = false;
            if (donem != null) {
                ekuant = donem.getGunler().size();
                status = donem.getStatus();
                locked = donem.isLocked();
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
                    locked
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
        String normalizedUnit = blankToNull(birimKodu);
        return basvuruRepository.findByBasvuruDonemiIdAndIliskiBitisTarihiIsNotNullOrderByIliskiBitisTarihiAsc(period.getId()).stream()
                .filter(basvuru -> normalizedUnit == null || normalizedUnit.equals(basvuru.getAtananBirimKodu()))
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
    public List<IzinRaporOgrenciResponse> leaveAndReportStudents(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        return leaveAndReportRecords(basvuruDonemiId, birimKodu, yil, ay).stream()
                .map(record -> new IzinRaporOgrenciResponse(
                        record.basvuru().getId(), record.basvuru().getStudent().getOgrenciNo(),
                        record.basvuru().getStudent().getTcKimlikNo(),
                        record.basvuru().getStudent().getAdSoyad(), record.basvuru().getAtananBirimAdi(),
                        record.gun().getTarih(), record.gun().getDurum(), record.gun().getBelgeYolu() != null,
                        record.gun().getBelgeAdi()
                )).toList();
    }

    @Transactional(readOnly = true)
    public byte[] leaveAndReportCsv(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        StringBuilder csv = new StringBuilder("\uFEFFÖğrenci No;T.C. Kimlik;Ad Soyad;Birim;Tarih;Durum;Belge\r\n");
        leaveAndReportStudents(basvuruDonemiId, birimKodu, yil, ay).forEach(row -> csv
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
    public byte[] leaveAndReportDocumentsZip(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (LeaveAndReportRecord record : leaveAndReportRecords(basvuruDonemiId, birimKodu, yil, ay)) {
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
    public List<LocalDate> adminKapaliGunler(Long basvuruDonemiId, int yil, int ay) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        return closedDays(period.getId(), requireMonth(yil, ay));
    }

    @Transactional
    public List<LocalDate> saveAdminKapaliGunler(Long basvuruDonemiId, int yil, int ay, TakipKapaliGunKaydetRequest request) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        if (!period.isAktif()) {
            throw new ApiException(HttpStatus.CONFLICT, "Kapalı dönemlerde giriş günleri değiştirilemez.");
        }
        YearMonth month = requireMonth(yil, ay);
        Set<LocalDate> days = request.gunler() == null ? Set.of() : new HashSet<>(request.gunler());
        for (LocalDate date : days) {
            if (!YearMonth.from(date).equals(month)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, date + " seçilen aya ait değil.");
            }
        }
        takipKapaliGunRepository.deleteByBasvuruDonemiIdAndTarihBetween(period.getId(), month.atDay(1), month.atEndOfMonth());
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
        for (Basvuru basvuru : basvurular) {
            TakipDonem donem = donemler.get(basvuru.getId());
            if (donem == null || donem.getStatus() == TakipStatus.DRAFT) {
                gonderilmeyen++;
            } else if (donem.getStatus() == TakipStatus.SUBMITTED) {
                onayBekleyen++;
            } else if (donem.getStatus() == TakipStatus.APPROVED) {
                onayli++;
            }
        }
        boolean yazdirilabilir = !basvurular.isEmpty() && onayli == basvurular.size();
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
                    donem == null ? null : donem.getStatus()
            ));
        }
        return new BirimAylikRaporResponse(
                yil,
                ay,
                birimAdi,
                gunlukSaat.setScale(1, RoundingMode.HALF_UP),
                birimView ? yazdirilabilir : true,
                onayli,
                onayBekleyen,
                gonderilmeyen,
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
        if (birimKodu == null || birimKodu.isBlank()) {
            return basvuruRepository.findByAtananBirimKoduIsNotNullAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(donem.getId());
        }
        return basvuruRepository.findByAtananBirimKoduAndStatusAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
                birimKodu, ApplicationStatus.APPROVED, donem.getId());
    }

    private String reportBirimAdi(String birimKodu, BasvuruDonemi donem) {
        if (birimKodu == null || birimKodu.isBlank()) {
            return "Tüm birimler";
        }
        return assignedKesin(birimKodu, donem).stream()
                .map(Basvuru::getAtananBirimAdi)
                .filter(ad -> ad != null && !ad.isBlank())
                .findFirst()
                .orElse(birimKodu);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String csvValue(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private List<LeaveAndReportRecord> leaveAndReportRecords(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        YearMonth month = requireMonth(yil, ay);
        Map<Long, TakipDonem> donemler = loadDonemler(assignedKesin(blankToNull(birimKodu), period), yil, ay);
        return donemler.values().stream()
                .flatMap(donem -> donem.getGunler().stream().map(gun -> new LeaveAndReportRecord(donem.getBasvuru(), gun)))
                .filter(record -> YearMonth.from(record.gun().getTarih()).equals(month))
                .filter(record -> record.gun().getDurum() == PuantajDurum.IZINLI || record.gun().getDurum() == PuantajDurum.RAPORLU)
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
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, request.yil(), request.ay());
        assertEditable(donem);
        Set<LocalDate> days = request.gunler() == null ? Set.of() : new HashSet<>(request.gunler());
        for (LocalDate date : days) {
            if (!YearMonth.from(date).equals(month)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, date + " seçilen aya ait değil.");
            }
        }
        Map<LocalDate, TakipGun> existing = donem.getGunler().stream()
                .collect(Collectors.toMap(TakipGun::getTarih, Function.identity()));
        assertMonthlyQuotaNotExceeded(donem.getBasvuru(), month, existing.keySet(), days);
        assertWeeklyLimitNotExceeded(month, days);
        assertClosedDaysUnchanged(donem.getBasvuru(), month, existing.keySet(), days);
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
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, request.yil(), request.ay());
        assertEditable(donem);
        Map<LocalDate, TakipGun> byDate = donem.getGunler().stream()
                .collect(Collectors.toMap(TakipGun::getTarih, Function.identity()));
        if (request.kayitlar() != null) {
            assertPermissionLimit(basvuruId, byDate, request.kayitlar());
            for (PuantajKaydetRequest.Kayit kayit : request.kayitlar()) {
                TakipGun gun = byDate.get(kayit.tarih());
                if (gun == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, kayit.tarih() + " EK-6 günü değil.");
                }
                assertEntryOpen(donem.getBasvuru(), kayit.tarih());
                assertPuantajDate(kayit.tarih());
                PuantajDurum onceki = gun.getDurum();
                gun.setDurum(kayit.durum());
                if (kayit.durum() == PuantajDurum.GELMEDI && donem.getBasvuru().getIliskiBitisTarihi() == null) {
                    donem.getBasvuru().setIliskiBitisTarihi(month.plusMonths(1).atDay(1));
                }
                if (kayit.durum() != PuantajDurum.IZINLI && kayit.durum() != PuantajDurum.RAPORLU
                        && (onceki == PuantajDurum.IZINLI || onceki == PuantajDurum.RAPORLU)) {
                    fileStorageService.deleteQuietly(gun.getBelgeYolu());
                    gun.setBelgeYolu(null);
                    gun.setBelgeAdi(null);
                    gun.setIcerikTipi(null);
                }
            }
        }
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
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, yil, ay);
        assertEditable(donem);
        assertEntryOpen(donem.getBasvuru(), tarih);
        TakipGun gun = donem.getGunler().stream()
                .filter(item -> item.getTarih().equals(tarih))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Önce EK-6 günü seçilmelidir."));
        assertPuantajDate(tarih);
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
    public TakipDonemResponse submit(String birimKodu, Long basvuruId, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        TakipDonem donem = getOrCreateEntity(birimKodu, basvuruId, yil, ay);
        assertEditable(donem);
        Set<LocalDate> quotaDays = quotaDays(donem.getBasvuru(), month);
        List<String> ekuantErrors = WorkScheduleRules.validateMonthlyQuota(month, quotaDays, daysPerWeek);
        if (!ekuantErrors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ekuantErrors.getFirst());
        }
        List<String> weeklyErrors = WorkScheduleRules.validateEkuant(
                month, actualMonthDays(donem.getBasvuru(), month), daysPerWeek);
        if (!weeklyErrors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, weeklyErrors.getFirst());
        }
        if (donem.getGunler().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Göndermeden önce EK-6 günleri seçilmelidir.");
        }
        for (TakipGun gun : donem.getGunler()) {
            if (gun.getDurum() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, gun.getTarih() + " için puantaj girilmedi.");
            }
            if ((gun.getDurum() == PuantajDurum.IZINLI || gun.getDurum() == PuantajDurum.RAPORLU)
                    && (gun.getBelgeYolu() == null || gun.getBelgeYolu().isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        gun.getTarih() + " izinli/raporlu olduğu için dilekçe veya rapor yüklenmelidir.");
            }
        }
        donem.setStatus(TakipStatus.SUBMITTED);
        donem.setGonderimTarihi(Instant.now());
        donem.setOnayTarihi(null);
        return toResponse(takipDonemRepository.save(donem), month);
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
        if (donem.getStatus() != TakipStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca gönderilmiş kayıtlar onaylanabilir.");
        }
        donem.setStatus(TakipStatus.APPROVED);
        donem.setOnayTarihi(Instant.now());
        return toResponse(takipDonemRepository.save(donem), month);
    }

    @Transactional
    public int adminApproveSubmitted(Long basvuruDonemiId, String birimKodu, int yil, int ay) {
        YearMonth month = requireMonth(yil, ay);
        BasvuruDonemi period = basvuruDonemiService.resolveForAdmin(basvuruDonemiId);
        String normalizedUnit = blankToNull(birimKodu);
        if (normalizedUnit == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Toplu onay için birim seçilmelidir.");
        }
        List<Basvuru> students = assignedKesin(normalizedUnit, period).stream()
                .filter(basvuru -> basvuru.canWorkIn(month))
                .toList();
        Map<Long, TakipDonem> donemler = loadDonemler(students, yil, ay);
        Instant now = Instant.now();
        int count = 0;
        for (Basvuru basvuru : students) {
            TakipDonem donem = donemler.get(basvuru.getId());
            if (donem != null && donem.getStatus() == TakipStatus.SUBMITTED) {
                donem.setStatus(TakipStatus.APPROVED);
                donem.setOnayTarihi(now);
                count++;
            }
        }
        if (count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Onaylanacak gönderilmiş kayıt bulunamadı.");
        }
        takipDonemRepository.saveAll(donemler.values());
        return count;
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
            return takipDonemRepository.save(created);
        });
    }

    private Basvuru requireAssigned(String birimKodu, Long basvuruId, YearMonth month) {
        return basvuruRepository.findDetailedByIdAndAtananBirimKoduAndBasvuruDonemiAktifTrue(basvuruId, birimKodu)
                .filter(basvuru -> basvuru.isAssigned() && basvuru.canWorkIn(month))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bu birime atanmış öğrenci bulunamadı."));
    }

    private void assertEditable(TakipDonem donem) {
        if (donem.isLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "Gönderilen ay değiştirilemez.");
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
        return new TakipDonemResponse(
                null,
                basvuru.getId(),
                month.getYear(),
                month.getMonthValue(),
                TakipStatus.DRAFT,
                false,
                null,
                List.of(),
                List.of(),
                ekuantWarnings(basvuru, month),
                closedDays(basvuru.getBasvuruDonemi().getId(), month),
                quotaDays(basvuru, month).size(),
                WorkScheduleRules.monthlyQuota(daysPerWeek),
                countPermissionDays(basvuru.getId()),
                MAX_IZIN_GUNU,
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
        List<String> warnings = ekuantWarnings(donem.getBasvuru(), month);
        return new TakipDonemResponse(
                donem.getId(),
                donem.getBasvuru().getId(),
                donem.getYil(),
                donem.getAy(),
                donem.getStatus(),
                donem.isLocked(),
                donem.getGonderimTarihi(),
                ekuant,
                puantaj,
                warnings,
                closedDays(donem.getBasvuru().getBasvuruDonemi().getId(), month),
                quotaDays.size(),
                WorkScheduleRules.monthlyQuota(daysPerWeek),
                countPermissionDays(donem.getBasvuru().getId()),
                MAX_IZIN_GUNU,
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
        List<String> warnings = new ArrayList<>(
                WorkScheduleRules.validateMonthlyQuota(month, quotaDays(basvuru, month), daysPerWeek));
        warnings.addAll(WorkScheduleRules.validateEkuant(month, actualMonthDays(basvuru, month), daysPerWeek));
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
                        locked = donem.isLocked();
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
