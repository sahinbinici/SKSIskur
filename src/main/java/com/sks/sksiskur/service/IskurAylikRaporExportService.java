package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.TakipStatus;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.takip.WorkScheduleRules;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IskurAylikRaporExportService {

    private static final String TEMPLATE = "/templates/iskur-aylik-bordro-sablonu.xlsm";
    private static final String MARK_GELDI = "██";
    private static final String MARK_GELMEDI = "D";
    private static final String MARK_IZIN = "Ü";
    private static final String MARK_RAPOR = "S";
    private static final String MARK_EK6 = "X";
    private static final String SHEET_KATILIMCI = "!!!KATILIMCI LİSTESİ!!!";
    private static final String SHEET_DEVAMSIZLIK = "1-Devamsızlık Formu (Ek-4)";
    private static final String SHEET_EK6 = "6-Devam Gün Çizelgesi";

    private static final String[] MONTHS_TR = {
            "OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN",
            "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK"
    };

    private static final int TEMPLATE_SAMPLE_ROWS = 150;

    private final TakipService takipService;

    public IskurAylikRaporExportService(TakipService takipService) {
        this.takipService = takipService;
    }

    public byte[] export(Long donemId, String birimKodu, int yil, int ay) {
        BirimAylikRaporResponse rapor = takipService.adminMonthlyReport(donemId, birimKodu, yil, ay);
        List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler = rapor.ogrenciler().stream()
                .filter(student -> student.status() == TakipStatus.APPROVED)
                .sorted(Comparator.comparing(student -> adSoyad(student).toUpperCase(Locale.forLanguageTag("tr-TR"))))
                .toList();
        if (ogrenciler.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Onaylanmış puantaj yok. Önce Aylık devam > Puantaj onayı ile kayıtları onaylayın. "
                            + "Doldurulan sayfalar KATILIMCI LİSTESİ, Devamsızlık Formu ve Devam Gün Çizelgesi’dir; Bordro boş kalır.");
        }
        return buildWorkbook(ogrenciler, YearMonth.of(yil, ay));
    }

    private byte[] buildWorkbook(List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler, YearMonth month) {
        try (InputStream input = getClass().getResourceAsStream(TEMPLATE)) {
            if (input == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "İŞKUR bordro şablonu bulunamadı.");
            }
            try (Workbook workbook = new XSSFWorkbook(input); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                fillSksOnayliListe(workbook, ogrenciler, month);
                fillKatilimciListesi(workbook, ogrenciler);
                fillDevamsizlikFormu(workbook, ogrenciler, month);
                fillDevamGunCizelgesi(workbook, ogrenciler, month);
                workbook.setActiveSheet(0);
                workbook.setFirstVisibleTab(0);
                workbook.setForceFormulaRecalculation(true);
                workbook.write(output);
                return output.toByteArray();
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "İŞKUR aylık paket dosyası oluşturulamadı: " + ex.getMessage());
        }
    }

    private void fillSksOnayliListe(
            Workbook workbook,
            List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler,
            YearMonth month
    ) {
        String name = "SKS-Onayli-Liste";
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            sheet = workbook.createSheet(name);
        }
        workbook.setSheetOrder(name, 0);
        Row header = getOrCreateRow(sheet, 0);
        setCell(header, 0, "SIRA");
        setCell(header, 1, "ADI SOYADI");
        setCell(header, 2, "TC KIMLIK NO");
        setCell(header, 3, "IBAN");
        setCell(header, 4, "GELDI GUNLERI");
        setCell(header, 5, "DONEM");
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            Row row = getOrCreateRow(sheet, i + 1);
            setCell(row, 0, i + 1);
            setCell(row, 1, adSoyad(ogrenci));
            setCell(row, 2, nullToEmpty(ogrenci.tcKimlikNo()));
            setCell(row, 3, nullToEmpty(ogrenci.iban()));
            setCell(row, 4, formatDays(ogrenci.geldiGunler()));
            setCell(row, 5, MONTHS_TR[month.getMonthValue() - 1] + " " + month.getYear());
        }
        for (int col = 0; col <= 5; col++) {
            sheet.autoSizeColumn(col);
        }
    }

    private String formatDays(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (LocalDate date : dates) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(date.getDayOfMonth());
        }
        return sb.toString();
    }

    private Sheet fillKatilimciListesi(Workbook workbook, List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler) {
        Sheet sheet = requireSheet(workbook, SHEET_KATILIMCI);
        clearRows(sheet, 1, clearEndRow(1, ogrenciler.size()), 1, 3);
        int rowIndex = 1;
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            Row row = getOrCreateRow(sheet, rowIndex++);
            setCell(row, 0, i + 1);
            setCell(row, 1, adSoyad(ogrenci));
            setCell(row, 2, nullToEmpty(ogrenci.tcKimlikNo()));
            setCell(row, 3, nullToEmpty(ogrenci.iban()));
        }
        return sheet;
    }

    private void fillDevamsizlikFormu(
            Workbook workbook,
            List<BirimAylikRaporResponse.RaporOgrenci> ogrenciler,
            YearMonth month
    ) {
        Sheet sheet = requireSheet(workbook, SHEET_DEVAMSIZLIK);
        setCell(getOrCreateRow(sheet, 0), 2, month.getYear());
        setCell(getOrCreateRow(sheet, 0), 15, MONTHS_TR[month.getMonthValue() - 1]);
        clearRows(sheet, 6, clearEndRow(6, ogrenciler.size()), 1, 33);
        int rowIndex = 6;
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            Row row = getOrCreateRow(sheet, rowIndex++);
            setCell(row, 0, i + 1);
            setCell(row, 1, nullToEmpty(ogrenci.tcKimlikNo()));
            setCell(row, 2, adSoyad(ogrenci));
            Map<Integer, String> marks = dayMarks(ogrenci);
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                String mark = marks.get(day);
                if (mark != null) {
                    setCell(row, 2 + day, mark);
                }
            }
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
        clearRows(sheet, 8, clearEndRow(8, ogrenciler.size()), 3, 37);
        List<List<LocalDate>> weeks = WorkScheduleRules.calendarWeeks(month);
        int rowIndex = 8;
        for (int i = 0; i < ogrenciler.size(); i++) {
            BirimAylikRaporResponse.RaporOgrenci ogrenci = ogrenciler.get(i);
            Row row = getOrCreateRow(sheet, rowIndex++);
            setCell(row, 0, i + 1);
            if (row.getCell(1) == null || row.getCell(1).getCellType() != CellType.FORMULA) {
                setCell(row, 1, nullToEmpty(ogrenci.tcKimlikNo()));
                setCell(row, 2, adSoyad(ogrenci));
            }
            Set<LocalDate> geldi = new HashSet<>(ogrenci.geldiGunler() == null ? List.of() : ogrenci.geldiGunler());
            int col = 3;
            for (List<LocalDate> week : weeks) {
                for (LocalDate day : week) {
                    if (col > 37) {
                        break;
                    }
                    if (day != null && geldi.contains(day)) {
                        setCell(row, col, MARK_EK6);
                    }
                    col++;
                }
            }
        }
    }

    private Map<Integer, String> dayMarks(BirimAylikRaporResponse.RaporOgrenci ogrenci) {
        Map<Integer, String> marks = new HashMap<>();
        putDays(marks, ogrenci.geldiGunler(), MARK_GELDI);
        putDays(marks, ogrenci.gelmediGunler(), MARK_GELMEDI);
        putDays(marks, ogrenci.izinliGunler(), MARK_IZIN);
        putDays(marks, ogrenci.raporluGunler(), MARK_RAPOR);
        return marks;
    }

    private void putDays(Map<Integer, String> marks, List<LocalDate> dates, String mark) {
        if (dates == null) {
            return;
        }
        for (LocalDate date : dates) {
            marks.put(date.getDayOfMonth(), mark);
        }
    }

    private Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet != null) {
            return sheet;
        }
        String needle = normalizeSheetName(name);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String candidate = normalizeSheetName(workbook.getSheetName(i));
            if (candidate.contains(needle) || needle.contains(candidate)) {
                return workbook.getSheetAt(i);
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Şablonda '" + name + "' sayfası bulunamadı.");
    }

    private static String normalizeSheetName(String name) {
        if (name == null) {
            return "";
        }
        return name.toUpperCase(Locale.forLanguageTag("tr-TR"))
                .replace('İ', 'I')
                .replace('Ş', 'S')
                .replace('Ğ', 'G')
                .replace('Ü', 'U')
                .replace('Ö', 'O')
                .replace('Ç', 'C')
                .replaceAll("[^A-Z0-9]", "");
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
