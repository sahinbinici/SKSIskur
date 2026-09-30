package com.sks.sksiskur.service;

import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.takip.WorkScheduleRules;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class IskurAylikRaporExportService {

    private static final String TEMPLATE = "/templates/iskur-aylik-rapor-sablonu.xlsx";
    private static final String MARK = "██";
    private static final String SHEET_KATILIMCI = "!!!KATILIMCI LİSTESİ!!!";
    private static final String SHEET_DEVAMSIZLIK = "1-Devamsızlık Formu (Ek-4)";
    private static final String SHEET_BANKA = "5-Banka Listesi";
    private static final String SHEET_EK6 = "6-Devam Gün Çizelgesi";

    private static final String[] MONTHS_TR = {
            "OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN",
            "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK"
    };

    private final TakipService takipService;

    @Value("${app.iskur.cep-harciligi:1375}")
    private int cepHarciligi;

    @Value("${app.iskur.kurum-adi:GAZİANTEP REKTÖRLÜĞÜ}")
    private String kurumAdi;

    @Value("${app.iskur.banka-subesi:T.C. HALK BANKASI ÜNİVERSİTE UYDU ŞUBESİ}")
    private String bankaSubesi;

    @Value("${app.iskur.banka-hesap:TR310001200133800004000406}")
    private String bankaHesap;

    public IskurAylikRaporExportService(TakipService takipService) {
        this.takipService = takipService;
    }

    public byte[] export(Long donemId, String birimKodu, int yil, int ay) {
        BirimAylikRaporResponse rapor = takipService.adminMonthlyReport(donemId, birimKodu, yil, ay);
        List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler = rapor.ogrenciler().stream()
                .sorted(Comparator.comparing(student -> adSoyad(student).toUpperCase(Locale.forLanguageTag("tr-TR"))))
                .toList();
        return buildWorkbook(ogrenciler, YearMonth.of(yil, ay));
    }

    private byte[] buildWorkbook(List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler, YearMonth month) {
        try (InputStream input = getClass().getResourceAsStream(TEMPLATE)) {
            if (input == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "İŞKUR rapor şablonu bulunamadı.");
            }
            try (Workbook workbook = new XSSFWorkbook(input); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                fillKatilimciListesi(workbook, ogrenciler);
                fillDevamsizlikFormu(workbook, ogrenciler, month);
                fillBankaListesi(workbook, ogrenciler, month);
                fillDevamGunCizelgesi(workbook, ogrenciler, month);
                workbook.setForceFormulaRecalculation(false);
                workbook.write(output);
                return output.toByteArray();
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "İŞKUR aylık paket dosyası oluşturulamadı.");
        }
    }

    private static final int TEMPLATE_SAMPLE_ROWS = 150;

    private void fillKatilimciListesi(Workbook workbook, List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler) {
        Sheet sheet = requireSheet(workbook, SHEET_KATILIMCI);
        clearRows(sheet, 1, clearEndRow(1, ogrenciler.size()), 0, 3);
        int rowIndex = 1;
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            Row row = getOrCreateRow(sheet, rowIndex++);
            setCell(row, 0, i + 1);
            setCell(row, 1, adSoyad(ogrenci));
            setCell(row, 2, nullToEmpty(ogrenci.tcKimlikNo()));
            setCell(row, 3, nullToEmpty(ogrenci.iban()));
        }
    }

    private void fillDevamsizlikFormu(
            Workbook workbook,
            List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler,
            YearMonth month
    ) {
        Sheet sheet = requireSheet(workbook, SHEET_DEVAMSIZLIK);
        setCell(getOrCreateRow(sheet, 0), 2, month.getYear());
        setCell(getOrCreateRow(sheet, 0), 15, MONTHS_TR[month.getMonthValue() - 1]);
        clearRows(sheet, 7, clearEndRow(7, ogrenciler.size()), 0, 34);
        int rowIndex = 7;
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            Row row = getOrCreateRow(sheet, rowIndex++);
            setCell(row, 0, i + 1);
            setCell(row, 1, nullToEmpty(ogrenci.tcKimlikNo()));
            setCell(row, 2, adSoyad(ogrenci));
            Set<Integer> geldiGunler = new HashSet<>();
            for (LocalDate date : ogrenci.geldiGunler()) {
                geldiGunler.add(date.getDayOfMonth());
            }
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                if (geldiGunler.contains(day)) {
                    setCell(row, 2 + day, MARK);
                }
            }
        }
    }

    private void fillBankaListesi(
            Workbook workbook,
            List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler,
            YearMonth month
    ) {
        Sheet sheet = requireSheet(workbook, SHEET_BANKA);
        setCell(getOrCreateRow(sheet, 1), 3, kurumAdi);
        setCell(getOrCreateRow(sheet, 2), 3, bankaSubesi);
        setCell(getOrCreateRow(sheet, 2), 7, MONTHS_TR[month.getMonthValue() - 1] + " " + month.getYear());
        setCell(getOrCreateRow(sheet, 3), 3, bankaHesap);
        clearRows(sheet, 5, clearEndRow(5, ogrenciler.size()), 1, 7);
        int rowIndex = 5;
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            int normalMaas = ogrenci.toplamGun() * cepHarciligi;
            Row row = getOrCreateRow(sheet, rowIndex++);
            setCell(row, 1, i + 1);
            setCell(row, 2, adSoyad(ogrenci));
            setCell(row, 3, nullToEmpty(ogrenci.tcKimlikNo()));
            setCell(row, 4, nullToEmpty(ogrenci.iban()));
            setCell(row, 5, normalMaas);
            setCell(row, 6, 0);
            setCell(row, 7, normalMaas);
        }
    }

    private void fillDevamGunCizelgesi(
            Workbook workbook,
            List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler,
            YearMonth month
    ) {
        Sheet sheet = requireSheet(workbook, SHEET_EK6);
        setCell(getOrCreateRow(sheet, 3), 2, MONTHS_TR[month.getMonthValue() - 1]);
        setCell(getOrCreateRow(sheet, 4), 2, month.getYear());
        clearRows(sheet, 8, clearEndRow(8, ogrenciler.size()), 0, 39);
        List<List<LocalDate>> weeks = WorkScheduleRules.calendarWeeks(month);
        int rowIndex = 8;
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            Row row = getOrCreateRow(sheet, rowIndex++);
            Set<LocalDate> selected = new HashSet<>(ogrenci.ekuantGunler());
            setCell(row, 0, i + 1);
            setCell(row, 1, nullToEmpty(ogrenci.tcKimlikNo()));
            setCell(row, 2, adSoyad(ogrenci));
            int col = 3;
            for (List<LocalDate> week : weeks) {
                for (LocalDate day : week) {
                    if (col > 39) {
                        break;
                    }
                    if (day != null && selected.contains(day)) {
                        setCell(row, col, MARK);
                    }
                    col++;
                }
            }
        }
    }

    private Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Şablonda '" + name + "' sayfası bulunamadı.");
        }
        return sheet;
    }

    private int clearEndRow(int startRow, int studentCount) {
        return Math.min(startRow + Math.max(studentCount, TEMPLATE_SAMPLE_ROWS) + 20, startRow + 2000);
    }

    private void clearRows(Sheet sheet, int startRow, int endRow, int startCol, int endCol) {
        int lastRow = Math.min(endRow, sheet.getLastRowNum());
        for (int rowIndex = startRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int col = startCol; col <= endCol; col++) {
                Cell cell = row.getCell(col);
                if (cell != null) {
                    cell.setBlank();
                }
            }
        }
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private void setCell(Row row, int col, Object value) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
        }
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static String adSoyad(BirimAylikRaporResponse.RaporOgrenci ogrenci) {
        return (nullToEmpty(ogrenci.ad()) + " " + nullToEmpty(ogrenci.soyad())).trim().toUpperCase(Locale.forLanguageTag("tr-TR"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
