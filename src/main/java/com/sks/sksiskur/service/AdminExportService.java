package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.PuantajDurum;
import com.sks.sksiskur.domain.TakipStatus;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
import com.sks.sksiskur.web.dto.AdminTakipOzetResponse;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.BelgeYuklemeFiltre;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.IzinRaporOgrenciResponse;
import com.sks.sksiskur.web.dto.KayitListeFiltre;
import com.sks.sksiskur.web.dto.KayitListesiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AdminExportService {

    private final AdminApplicationService adminApplicationService;
    private final TakipService takipService;
    private final IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository;
    private final BasvuruDonemiService basvuruDonemiService;
    private final ExcelExportService excelExportService;
    private final CetvelExcelExportService cetvelExcelExportService;
    private final FileStorageService fileStorageService;

    public AdminExportService(
            AdminApplicationService adminApplicationService,
            TakipService takipService,
            IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository,
            BasvuruDonemiService basvuruDonemiService,
            ExcelExportService excelExportService,
            CetvelExcelExportService cetvelExcelExportService,
            FileStorageService fileStorageService
    ) {
        this.adminApplicationService = adminApplicationService;
        this.takipService = takipService;
        this.iskurBasvuruKaydiRepository = iskurBasvuruKaydiRepository;
        this.basvuruDonemiService = basvuruDonemiService;
        this.excelExportService = excelExportService;
        this.cetvelExcelExportService = cetvelExcelExportService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public byte[] basvurularExcel(
            Long donemId,
            ApplicationStatus status,
            String query,
            String assignedTo,
            DocumentType belgeTipi,
            BelgeYuklemeFiltre belgeYukleme
    ) {
        List<BasvuruResponse> items = adminApplicationService.list(
                donemId, status, query, assignedTo, belgeTipi, belgeYukleme);
        List<String> headers = List.of(
                "Sıra", "Öğrenci No", "T.C. Kimlik", "Ad", "Soyad", "Fakülte / Bölüm",
                "Atanan Birim", "İnceleme Sorumlusu", "Durum", "Kayıt", "Belge", "Gönderim"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BasvuruResponse item = items.get(i);
            rows.add(excelRow(
                    i + 1,
                    item.student().ogrenciNo(),
                    item.student().tcKimlikNo(),
                    item.student().ad(),
                    item.student().soyad(),
                    faculty(item.student().fakulte(), item.student().program(), item.student().bolum()),
                    item.atananBirimAdi(),
                    item.atananAdmin(),
                    statusLabel(item.status()),
                    kayitLabel(item),
                    item.belgeler().size() + "/5",
                    item.gonderimTarihi()
            ));
        }
        return excelExportService.singleSheet("Başvurular", headers, rows);
    }

    @Transactional(readOnly = true)
    public byte[] basvurularBelgeBazindaZip(
            Long donemId,
            ApplicationStatus status,
            String query,
            String assignedTo,
            DocumentType belgeTipi,
            BelgeYuklemeFiltre belgeYukleme
    ) {
        if (belgeTipi == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Toplu indirme için belge türü seçmelisiniz.");
        }
        if (belgeYukleme == BelgeYuklemeFiltre.YOK) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Eksik belge filtresinde indirilecek dosya bulunmaz.");
        }
        List<Basvuru> basvurular = adminApplicationService.listBasvurular(
                donemId, status, query, assignedTo, belgeTipi, belgeYukleme);
        String belgeFolder = belgeTipi.name().toLowerCase(Locale.ROOT);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            Set<String> usedNames = new HashSet<>();
            int fileCount = 0;
            for (Basvuru basvuru : basvurular) {
                Student student = basvuru.getStudent();
                String studentPrefix = safeZipName(student.getOgrenciNo() + "_" + student.getAd() + "_" + student.getSoyad());
                Map<DocumentType, Integer> counters = new EnumMap<>(DocumentType.class);
                List<BasvuruBelgesi> belgeler = basvuru.getBelgeler().stream()
                        .filter(belge -> belge.getBelgeTipi() == belgeTipi)
                        .sorted(Comparator.comparing(BasvuruBelgesi::getYuklemeTarihi))
                        .toList();
                for (BasvuruBelgesi belge : belgeler) {
                    Path path = fileStorageService.resolve(belge.getSaklamaYolu());
                    if (!Files.exists(path)) {
                        continue;
                    }
                    int index = counters.merge(belge.getBelgeTipi(), 1, Integer::sum);
                    String entryName = uniqueZipEntry(
                            usedNames,
                            belgeFolder + "/" + studentPrefix + "_" + zipBelgeFileName(belge, index)
                    );
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                    fileCount++;
                }
            }
            zip.finish();
            if (fileCount == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Seçilen filtreye uygun indirilebilir dosya bulunamadı.");
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Belge dosyaları hazırlanamadı.");
        }
    }

    private static String zipBelgeFileName(BasvuruBelgesi belge, int index) {
        String extension = fileExtension(belge.getOrijinalAd(), belge.getSaklamaYolu());
        String base = belge.getOrijinalAd();
        if (base == null || base.isBlank()) {
            base = belge.getBelgeTipi().name().toLowerCase(Locale.ROOT);
        } else {
            base = base.replaceAll("\\.[^.]+$", "");
        }
        base = safeZipName(base);
        if (index > 1) {
            base = base + "_" + index;
        }
        if (belge.getHaneUyesiAdi() != null && !belge.getHaneUyesiAdi().isBlank()) {
            base = base + "_" + safeZipName(belge.getHaneUyesiAdi());
        }
        return base + extension;
    }

    private static String fileExtension(String originalName, String storagePath) {
        String candidate = originalName != null && originalName.contains(".") ? originalName : storagePath;
        int dot = candidate.lastIndexOf('.');
        if (dot < 0 || dot == candidate.length() - 1) {
            return "";
        }
        return candidate.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String uniqueZipEntry(Set<String> usedNames, String candidate) {
        String entry = safeZipName(candidate);
        if (usedNames.add(entry)) {
            return entry;
        }
        String base = entry;
        String extension = "";
        int dot = entry.lastIndexOf('.');
        if (dot > 0) {
            base = entry.substring(0, dot);
            extension = entry.substring(dot);
        }
        int suffix = 2;
        while (!usedNames.add(base + "_" + suffix + extension)) {
            suffix++;
        }
        return base + "_" + suffix + extension;
    }

    private static String safeZipName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Transactional(readOnly = true)
    public byte[] kayitListesiExcel() {
        KayitListesiResponse data = adminApplicationService.kayitListesi(KayitListeFiltre.TUMU);
        List<KayitListesiResponse.Satir> onayli = data.ogrenciler();
        List<KayitListesiResponse.Satir> kesin = onayli.stream()
                .filter(row -> Boolean.TRUE.equals(row.kesinListede())).toList();
        List<KayitListesiResponse.Satir> disinda = onayli.stream()
                .filter(row -> Boolean.FALSE.equals(row.kesinListede())).toList();
        List<List<Object>> eslesmeyen = data.listedeEslesmeyenler().stream()
                .map(row -> excelRow(
                        row.tcKimlikNo(),
                        row.ad(),
                        row.soyad(),
                        row.ogrenciNo()
                ))
                .toList();
        return excelExportService.workbook(List.of(
                kayitSheet("Onaylı Başvurular", onayli),
                kayitSheet("Kesin Listede", kesin),
                kayitSheet("Kesin Listede Değil", disinda),
                new ExcelExportService.SheetSpec(
                        "Listede Başvuru Yok",
                        List.of("T.C. Kimlik", "Ad", "Soyad", "Öğrenci No"),
                        eslesmeyen
                )
        ));
    }

    @Transactional(readOnly = true)
    public byte[] iskurListesiExcel(Long donemId) {
        var donem = basvuruDonemiService.resolveForAdmin(donemId);
        List<IskurBasvuruKaydi> kayitlar = iskurBasvuruKaydiRepository
                .findByBasvuruDonemiIdOrderByAdAscSoyadAsc(donem.getId());
        List<String> headers = List.of("Sıra", "T.C. Kimlik", "Ad", "Soyad", "Öğrenci No");
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < kayitlar.size(); i++) {
            IskurBasvuruKaydi kayit = kayitlar.get(i);
            rows.add(excelRow(
                    i + 1,
                    kayit.getTcKimlikNo(),
                    kayit.getAd(),
                    kayit.getSoyad(),
                    kayit.getOgrenciNo()
            ));
        }
        return excelExportService.singleSheet("İŞKUR Listesi", headers, rows);
    }

    @Transactional(readOnly = true)
    public byte[] takipOzetExcel(Long donemId, String birimKodu, int yil, int ay) {
        AdminTakipOzetResponse ozet = takipService.adminTakipOzet(donemId, birimKodu, yil, ay);
        List<String> headers = List.of(
                "Sıra", "Öğrenci No", "T.C. Kimlik", "Ad Soyad", "Birim Kodu", "Birim",
                "EK-6", "Geldi", "Gelmedi", "İzinli", "Raporlu", "Durum", "Gönderildi"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < ozet.ogrenciler().size(); i++) {
            AdminTakipOzetResponse.Satir row = ozet.ogrenciler().get(i);
            rows.add(excelRow(
                    i + 1,
                    row.ogrenciNo(),
                    row.tcKimlikNo(),
                    row.adSoyad(),
                    row.birimKodu(),
                    row.birimAdi(),
                    row.ekuant(),
                    row.geldi(),
                    row.gelmedi(),
                    row.izinli(),
                    row.raporlu(),
                    takipStatusLabel(row.status()),
                    row.locked() ? "Evet" : "Hayır"
            ));
        }
        return excelExportService.singleSheet("Takip Özeti", headers, rows);
    }

    @Transactional(readOnly = true)
    public byte[] takipRaporExcel(Long donemId, String birimKodu, int yil, int ay) {
        if (birimKodu == null || birimKodu.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EK-6 / puantaj Excel indirmek için birim seçilmelidir.");
        }
        BirimAylikRaporResponse rapor = takipService.adminMonthlyReport(donemId, birimKodu, yil, ay);
        if (rapor.ogrenciler().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Seçili birimde bu ay için öğrenci kaydı yok.");
        }
        boolean hasSubmitted = rapor.ogrenciler().stream()
                .anyMatch(ogrenci -> ogrenci.status() == com.sks.sksiskur.domain.TakipStatus.SUBMITTED
                        || ogrenci.status() == com.sks.sksiskur.domain.TakipStatus.APPROVED);
        if (!hasSubmitted) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Birim henüz bu ayı göndermedi. Excel, gönderilmiş veya onaylı kayıtlar için indirilebilir.");
        }
        return aylikRaporWorkbook(rapor);
    }

    @Transactional(readOnly = true)
    public byte[] birimTakipRaporExcel(String birimKodu, int yil, int ay) {
        BirimAylikRaporResponse rapor = takipService.monthlyReport(birimKodu, yil, ay);
        if (!rapor.yazdirilabilir()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "EK-6 ve puantaj cetvelleri yalnızca SKS onayı tamamlandığında indirilebilir.");
        }
        return aylikRaporWorkbook(rapor);
    }

    private byte[] aylikRaporWorkbook(BirimAylikRaporResponse rapor) {
        return cetvelExcelExportService.workbook(rapor);
    }

    @Transactional(readOnly = true)
    public byte[] izinRaporExcel(Long donemId, String birimKodu, int yil, int ay) {
        List<IzinRaporOgrenciResponse> rows = takipService.leaveAndReportStudents(donemId, birimKodu, yil, ay);
        List<String> headers = List.of("Öğrenci No", "T.C. Kimlik", "Ad Soyad", "Birim", "Tarih", "Durum", "Belge");
        List<List<Object>> data = rows.stream()
                .map(row -> excelRow(
                        row.ogrenciNo(),
                        row.tcKimlikNo(),
                        row.adSoyad(),
                        row.birimAdi(),
                        row.tarih(),
                        row.durum() == PuantajDurum.IZINLI ? "İzinli" : "Raporlu",
                        row.belgeAdi()
                ))
                .toList();
        return excelExportService.singleSheet("İzin ve Rapor", headers, data);
    }

    @Transactional(readOnly = true)
    public byte[] iliskisiKesilenlerExcel(Long donemId, String birimKodu) {
        var period = basvuruDonemiService.resolveForAdmin(donemId);
        List<String> headers = List.of("Öğrenci No", "T.C. Kimlik", "Ad Soyad", "Birim", "İlişki Kesilme Tarihi", "Başvuru Dönemi");
        List<List<Object>> rows = takipService.terminatedStudents(donemId, birimKodu).stream()
                .map(row -> excelRow(
                        row.ogrenciNo(),
                        row.tcKimlikNo(),
                        row.adSoyad(),
                        row.birimAdi(),
                        row.iliskiBitisTarihi(),
                        period.getAd()
                ))
                .toList();
        return excelExportService.singleSheet("İlişkisi Kesilenler", headers, rows);
    }

    private ExcelExportService.SheetSpec kayitSheet(String name, List<KayitListesiResponse.Satir> rows) {
        List<String> headers = List.of(
                "Sıra", "Öğrenci No", "T.C. Kimlik", "Ad", "Soyad", "Fakülte / Program", "Kesin Listede", "Atanan Birim"
        );
        List<List<Object>> data = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            KayitListesiResponse.Satir row = rows.get(i);
            data.add(excelRow(
                    i + 1,
                    row.ogrenciNo(),
                    row.tcKimlikNo(),
                    row.ad(),
                    row.soyad(),
                    faculty(row.fakulte(), row.program(), row.bolum()),
                    kesinListeLabel(row.kesinListede()),
                    row.atananBirimAdi()
            ));
        }
        return new ExcelExportService.SheetSpec(name, headers, data);
    }

    private static String kesinListeLabel(Boolean kesinListede) {
        if (kesinListede == null) return "Karşılaştırma bekliyor";
        return kesinListede ? "Evet" : "Hayır";
    }

    private static String faculty(String fakulte, String program, String bolum) {
        if (fakulte != null && !fakulte.isBlank()) return fakulte;
        if (program != null && !program.isBlank()) return program;
        return nullToEmpty(bolum);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<Object> excelRow(Object... values) {
        List<Object> row = new ArrayList<>(values.length);
        for (Object value : values) {
            row.add(value == null ? "" : value);
        }
        return row;
    }

    private static String statusLabel(ApplicationStatus status) {
        return switch (status) {
            case DRAFT -> "Taslak";
            case SUBMITTED -> "İncelemede";
            case RETURNED -> "İade edildi";
            case APPROVED -> "Onaylandı";
            case REJECTED -> "Reddedildi";
        };
    }

    private static String kayitLabel(BasvuruResponse item) {
        if (Boolean.TRUE.equals(item.kesinListede())) {
            return "Kesin listede";
        }
        if (Boolean.FALSE.equals(item.kesinListede())) {
            return "Kesin listede değil";
        }
        return item.status() == ApplicationStatus.APPROVED ? "İŞKUR kesin listesi bekleniyor" : "—";
    }

    private static String takipStatusLabel(TakipStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case SUBMITTED -> "Gönderildi";
            case APPROVED -> "Onaylandı";
            case DRAFT -> "Taslak";
        };
    }
}
