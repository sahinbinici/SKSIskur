package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.PuantajDurum;
import com.sks.sksiskur.domain.TakipStatus;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
import com.sks.sksiskur.web.dto.AdminTakipOzetResponse;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.IzinRaporOgrenciResponse;
import com.sks.sksiskur.web.dto.KayitListeFiltre;
import com.sks.sksiskur.web.dto.KayitListesiResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AdminExportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"));

    private final AdminApplicationService adminApplicationService;
    private final TakipService takipService;
    private final IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository;
    private final BasvuruDonemiService basvuruDonemiService;
    private final ExcelExportService excelExportService;

    public AdminExportService(
            AdminApplicationService adminApplicationService,
            TakipService takipService,
            IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository,
            BasvuruDonemiService basvuruDonemiService,
            ExcelExportService excelExportService
    ) {
        this.adminApplicationService = adminApplicationService;
        this.takipService = takipService;
        this.iskurBasvuruKaydiRepository = iskurBasvuruKaydiRepository;
        this.basvuruDonemiService = basvuruDonemiService;
        this.excelExportService = excelExportService;
    }

    @Transactional(readOnly = true)
    public byte[] basvurularExcel(Long donemId, ApplicationStatus status, String query, String assignedTo) {
        List<BasvuruResponse> items = adminApplicationService.list(donemId, status, query, assignedTo);
        List<String> headers = List.of(
                "Sıra", "Öğrenci No", "T.C. Kimlik", "Ad", "Soyad", "Fakülte / Bölüm",
                "Atanan Birim", "İnceleme Sorumlusu", "Durum", "Kayıt", "Belge", "Gönderim"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BasvuruResponse item = items.get(i);
            rows.add(List.of(
                    i + 1,
                    item.student().ogrenciNo(),
                    nullToEmpty(item.student().tcKimlikNo()),
                    item.student().ad(),
                    item.student().soyad(),
                    faculty(item.student().fakulte(), item.student().program(), item.student().bolum()),
                    nullToEmpty(item.atananBirimAdi()),
                    nullToEmpty(item.atananAdmin()),
                    statusLabel(item.status()),
                    kayitLabel(item),
                    item.belgeler().size() + "/5",
                    item.gonderimTarihi()
            ));
        }
        return excelExportService.singleSheet("Başvurular", headers, rows);
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
                .map(row -> List.<Object>of(
                        nullToEmpty(row.tcKimlikNo()),
                        row.ad(),
                        row.soyad(),
                        nullToEmpty(row.ogrenciNo())
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
            rows.add(List.of(
                    i + 1,
                    nullToEmpty(kayit.getTcKimlikNo()),
                    kayit.getAd(),
                    kayit.getSoyad(),
                    nullToEmpty(kayit.getOgrenciNo())
            ));
        }
        return excelExportService.singleSheet("İŞKUR Listesi", headers, rows);
    }

    @Transactional(readOnly = true)
    public byte[] takipOzetExcel(Long donemId, String birimKodu, int yil, int ay) {
        AdminTakipOzetResponse ozet = takipService.adminTakipOzet(donemId, birimKodu, yil, ay);
        List<String> headers = List.of(
                "Sıra", "Öğrenci No", "Ad Soyad", "Birim Kodu", "Birim",
                "EK-6", "Geldi", "Gelmedi", "İzinli", "Raporlu", "Durum", "Gönderildi"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < ozet.ogrenciler().size(); i++) {
            AdminTakipOzetResponse.Satir row = ozet.ogrenciler().get(i);
            rows.add(List.of(
                    i + 1,
                    row.ogrenciNo(),
                    row.adSoyad(),
                    nullToEmpty(row.birimKodu()),
                    nullToEmpty(row.birimAdi()),
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
        BirimAylikRaporResponse rapor = takipService.adminMonthlyReport(donemId, birimKodu, yil, ay);
        List<String> ekuantHeaders = List.of(
                "Sıra", "Öğrenci No", "T.C. Kimlik", "Ad", "Soyad", "IBAN", "EK-6 Günleri", "Gün Sayısı"
        );
        List<String> puantajHeaders = List.of(
                "Sıra", "Öğrenci No", "T.C. Kimlik", "Ad", "Soyad", "Geldiği Günler", "Gün Sayısı", "Toplam Saat"
        );
        List<List<Object>> ekuantRows = new ArrayList<>();
        List<List<Object>> puantajRows = new ArrayList<>();
        for (BirimAylikRaporResponse.RaporOgrenci ogrenci : rapor.ogrenciler()) {
            ekuantRows.add(List.of(
                    ogrenci.siraNo(),
                    ogrenci.ogrenciNo(),
                    nullToEmpty(ogrenci.tcKimlikNo()),
                    ogrenci.ad(),
                    ogrenci.soyad(),
                    nullToEmpty(ogrenci.iban()),
                    formatDays(ogrenci.ekuantGunler()),
                    ogrenci.ekuantGunler().size()
            ));
            puantajRows.add(List.of(
                    ogrenci.siraNo(),
                    ogrenci.ogrenciNo(),
                    nullToEmpty(ogrenci.tcKimlikNo()),
                    ogrenci.ad(),
                    ogrenci.soyad(),
                    formatDays(ogrenci.geldiGunler()),
                    ogrenci.toplamGun(),
                    ogrenci.toplamSaat()
            ));
        }
        return excelExportService.workbook(List.of(
                new ExcelExportService.SheetSpec("EK-6", ekuantHeaders, ekuantRows),
                new ExcelExportService.SheetSpec("Puantaj", puantajHeaders, puantajRows)
        ));
    }

    @Transactional(readOnly = true)
    public byte[] izinRaporExcel(Long donemId, String birimKodu, int yil, int ay) {
        List<IzinRaporOgrenciResponse> rows = takipService.leaveAndReportStudents(donemId, birimKodu, yil, ay);
        List<String> headers = List.of("Öğrenci No", "Ad Soyad", "Birim", "Tarih", "Durum", "Belge");
        List<List<Object>> data = rows.stream()
                .map(row -> List.<Object>of(
                        row.ogrenciNo(),
                        row.adSoyad(),
                        nullToEmpty(row.birimAdi()),
                        row.tarih(),
                        row.durum() == PuantajDurum.IZINLI ? "İzinli" : "Raporlu",
                        nullToEmpty(row.belgeAdi())
                ))
                .toList();
        return excelExportService.singleSheet("İzin ve Rapor", headers, data);
    }

    @Transactional(readOnly = true)
    public byte[] iliskisiKesilenlerExcel(Long donemId, String birimKodu) {
        var period = basvuruDonemiService.resolveForAdmin(donemId);
        List<String> headers = List.of("Öğrenci No", "Ad Soyad", "Birim", "İlişki Kesilme Tarihi", "Başvuru Dönemi");
        List<List<Object>> rows = takipService.terminatedStudents(donemId, birimKodu).stream()
                .map(row -> List.<Object>of(
                        row.ogrenciNo(),
                        row.adSoyad(),
                        nullToEmpty(row.birimAdi()),
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
            data.add(List.of(
                    i + 1,
                    row.ogrenciNo(),
                    nullToEmpty(row.tcKimlikNo()),
                    nullToEmpty(row.ad()),
                    nullToEmpty(row.soyad()),
                    faculty(row.fakulte(), row.program(), row.bolum()),
                    kesinListeLabel(row.kesinListede()),
                    nullToEmpty(row.atananBirimAdi())
            ));
        }
        return new ExcelExportService.SheetSpec(name, headers, data);
    }

    private static String kesinListeLabel(Boolean kesinListede) {
        if (kesinListede == null) return "Karşılaştırma bekliyor";
        return kesinListede ? "Evet" : "Hayır";
    }

    private static String formatDays(List<LocalDate> days) {
        return days.stream().map(DAY::format).collect(Collectors.joining(", "));
    }

    private static String faculty(String fakulte, String program, String bolum) {
        if (fakulte != null && !fakulte.isBlank()) return fakulte;
        if (program != null && !program.isBlank()) return program;
        return nullToEmpty(bolum);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
        return status == TakipStatus.SUBMITTED ? "Gönderildi" : "Taslak";
    }
}
