package com.sks.sksiskur.service;

import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.takip.WorkScheduleRules;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CetvelExcelExportService {

    private static final String[] MONTHS = {
            "OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN",
            "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK"
    };
    private static final String[] WEEKDAYS = {
            "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"
    };
    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    public byte[] workbook(BirimAylikRaporResponse rapor) {
        YearMonth month = YearMonth.of(rapor.yil(), rapor.ay());
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeEkuant(workbook.createSheet("EK-6"), rapor, month, styles);
            writePuantaj(workbook.createSheet("Puantaj"), rapor, month, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EK-6 / puantaj Excel dosyası oluşturulamadı.");
        }
    }

    private void writeEkuant(Sheet sheet, BirimAylikRaporResponse rapor, YearMonth month, Styles styles) {
        List<List<LocalDate>> weeks = WorkScheduleRules.calendarWeeks(month);
        int extraStart = Math.min(4, weeks.size());
        int lastCol = 2 + weeks.size() * 7;
        int row = 0;
        mergeTitle(sheet, row++, lastCol, "EK-6: İŞKUR Gençlik Programı Katılımcı Gün Çizelgesi", styles.title);
        mergeTitle(sheet, row++, lastCol, "T.C. GAZİANTEP ÜNİVERSİTESİ", styles.title);
        mergeTitle(sheet, row++, lastCol,
                upper(blank(rapor.birimAdi()) + " İŞKUR GENÇLİK KATILIMCI GÜN ÇİZELGESİ"), styles.subtitle);
        mergeTitle(sheet, row++, lastCol,
                "KATILIMCILAR İÇİN HAFTADA EN FAZLA 3 DEVAM GÜNÜ SEÇİNİZ  ·  "
                        + MONTHS[month.getMonthValue() - 1] + " (" + month.getYear() + ")",
                styles.note);

        Row weekRow = sheet.createRow(row++);
        Row dayRow = sheet.createRow(row++);
        setStyled(weekRow, 0, "SIRA", styles.header);
        setStyled(weekRow, 1, "TC", styles.header);
        setStyled(weekRow, 2, "AD SOYAD", styles.header);
        setStyled(dayRow, 0, "", styles.header);
        setStyled(dayRow, 1, "", styles.header);
        setStyled(dayRow, 2, "", styles.header);
        sheet.addMergedRegion(new CellRangeAddress(weekRow.getRowNum(), dayRow.getRowNum(), 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(weekRow.getRowNum(), dayRow.getRowNum(), 1, 1));
        sheet.addMergedRegion(new CellRangeAddress(weekRow.getRowNum(), dayRow.getRowNum(), 2, 2));

        int col = 3;
        int extraWeekCount = Math.max(0, weeks.size() - extraStart);
        for (int wi = 0; wi < weeks.size(); wi++) {
            boolean firstExtra = wi == extraStart && extraWeekCount > 0;
            String weekLabel = wi < extraStart ? (wi + 1) + ". HAFTA" : firstExtra ? "ARTIK GÜNLER" : "";
            int start = col;
            for (int di = 0; di < 7; di++) {
                LocalDate date = weeks.get(wi).get(di);
                String dayLabel = WEEKDAYS[di] + (date == null ? "" : "\n" + date.getDayOfMonth());
                setStyled(weekRow, col, di == 0 ? weekLabel : "", styles.header);
                setStyled(dayRow, col, dayLabel, styles.dayHead);
                col++;
            }
            if (wi < extraStart) {
                sheet.addMergedRegion(new CellRangeAddress(weekRow.getRowNum(), weekRow.getRowNum(), start, start + 6));
            }
        }
        if (extraWeekCount > 0) {
            int extraStartCol = 3 + extraStart * 7;
            sheet.addMergedRegion(new CellRangeAddress(
                    weekRow.getRowNum(), weekRow.getRowNum(), extraStartCol, lastCol));
        }

        for (BirimAylikRaporResponse.RaporOgrenci ogrenci : rapor.ogrenciler()) {
            Set<LocalDate> selected = new HashSet<>(
                    ogrenci.ekuantGunler() == null ? List.of() : ogrenci.ekuantGunler());
            Row data = sheet.createRow(row++);
            setStyled(data, 0, ogrenci.siraNo(), styles.center);
            setStyled(data, 1, blank(ogrenci.tcKimlikNo()), styles.center);
            setStyled(data, 2, upper(blank(ogrenci.ad()) + " " + blank(ogrenci.soyad())), styles.name);
            col = 3;
            for (List<LocalDate> week : weeks) {
                for (LocalDate date : week) {
                    boolean mark = date != null && selected.contains(date);
                    setStyled(data, col++, mark ? "X" : "", mark ? styles.mark : styles.center);
                }
            }
        }
        writeSignatures(sheet, row + 1, lastCol, styles);
        layout(sheet, lastCol, 3, 3.2);
        sheet.setColumnWidth(0, 1800);
        sheet.setColumnWidth(1, 3800);
        sheet.setColumnWidth(2, 7200);
        sheet.createFreezePane(3, 6);
    }

    private void writePuantaj(Sheet sheet, BirimAylikRaporResponse rapor, YearMonth month, Styles styles) {
        int daysInMonth = month.lengthOfMonth();
        int lastCol = 4 + daysInMonth + 2;
        int row = 0;
        mergeTitle(sheet, row++, lastCol, "T.C. GAZİANTEP ÜNİVERSİTESİ", styles.title);
        mergeTitle(sheet, row++, lastCol, upper(blank(rapor.birimAdi())), styles.subtitle);
        mergeTitle(sheet, row++, lastCol,
                "İŞKUR GENÇLİK PROGRAMI KAPSAMINDA ÇALIŞAN ÖĞRENCİLERİN "
                        + month.getYear() + " YILI " + MONTHS[month.getMonthValue() - 1]
                        + " AYI PUANTAJ CETVELİDİR",
                styles.note);

        Row head1 = sheet.createRow(row++);
        Row head2 = sheet.createRow(row++);
        String[] fixed = {"SIRA NO", "T.C. KİMLİK NUMARASI", "ADI", "SOYADI", "HALKBANK IBAN NO"};
        for (int i = 0; i < fixed.length; i++) {
            setStyled(head1, i, fixed[i], styles.header);
            setStyled(head2, i, "", styles.header);
            sheet.addMergedRegion(new CellRangeAddress(head1.getRowNum(), head2.getRowNum(), i, i));
        }
        int dayStart = 5;
        int dayEnd = 4 + daysInMonth;
        setStyled(head1, dayStart, "ÇALIŞTIĞI GÜNLER", styles.header);
        for (int d = 1; d <= daysInMonth; d++) {
            if (d > 1) {
                setStyled(head1, dayStart + d - 1, "", styles.header);
            }
            setStyled(head2, dayStart + d - 1, d, styles.dayHead);
        }
        sheet.addMergedRegion(new CellRangeAddress(head1.getRowNum(), head1.getRowNum(), dayStart, dayEnd));
        setStyled(head1, dayEnd + 1, "TOPLAM SAAT", styles.header);
        setStyled(head2, dayEnd + 1, "", styles.header);
        sheet.addMergedRegion(new CellRangeAddress(head1.getRowNum(), head2.getRowNum(), dayEnd + 1, dayEnd + 1));
        setStyled(head1, dayEnd + 2, "TOPLAM GÜN", styles.header);
        setStyled(head2, dayEnd + 2, "", styles.header);
        sheet.addMergedRegion(new CellRangeAddress(head1.getRowNum(), head2.getRowNum(), dayEnd + 2, dayEnd + 2));

        BigDecimal gunluk = rapor.gunlukSaat() == null ? new BigDecimal("7.5") : rapor.gunlukSaat();
        for (BirimAylikRaporResponse.RaporOgrenci ogrenci : rapor.ogrenciler()) {
            Set<Integer> geldi = new HashSet<>();
            if (ogrenci.geldiGunler() != null) {
                for (LocalDate date : ogrenci.geldiGunler()) {
                    geldi.add(date.getDayOfMonth());
                }
            }
            Row data = sheet.createRow(row++);
            setStyled(data, 0, ogrenci.siraNo(), styles.center);
            setStyled(data, 1, blank(ogrenci.tcKimlikNo()), styles.center);
            setStyled(data, 2, upper(blank(ogrenci.ad())), styles.name);
            setStyled(data, 3, upper(blank(ogrenci.soyad())), styles.name);
            setStyled(data, 4, blank(ogrenci.iban()), styles.iban);
            for (int d = 1; d <= daysInMonth; d++) {
                boolean mark = geldi.contains(d);
                setStyled(data, 4 + d, mark ? gunluk : "", mark ? styles.hours : styles.center);
            }
            setStyled(data, dayEnd + 1, ogrenci.toplamGun() > 0 ? ogrenci.toplamSaat() : "", styles.hours);
            setStyled(data, dayEnd + 2, ogrenci.toplamGun() > 0 ? ogrenci.toplamGun() : "", styles.center);
        }
        writeSignatures(sheet, row + 1, lastCol, styles);
        layout(sheet, lastCol, 5, 2.6);
        sheet.setColumnWidth(0, 1800);
        sheet.setColumnWidth(1, 4200);
        sheet.setColumnWidth(2, 4200);
        sheet.setColumnWidth(3, 4200);
        sheet.setColumnWidth(4, 7200);
        sheet.setColumnWidth(dayEnd + 1, 2800);
        sheet.setColumnWidth(dayEnd + 2, 2400);
        sheet.createFreezePane(5, 5);
    }

    private void writeSignatures(Sheet sheet, int row, int lastCol, Styles styles) {
        Row sig = sheet.createRow(Math.max(row, 0));
        setStyled(sig, 0, "HAZIRLAYAN", styles.note);
        if (lastCol >= 8) {
            sheet.addMergedRegion(new CellRangeAddress(sig.getRowNum(), sig.getRowNum(), 0, 3));
            setStyled(sig, Math.max(8, lastCol - 6), "ONAYLAYAN", styles.note);
        }
    }

    private void layout(Sheet sheet, int lastCol, int identityCols, double dayWidth) {
        sheet.setFitToPage(true);
        PrintSetup print = sheet.getPrintSetup();
        print.setLandscape(true);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.setRepeatingRows(new CellRangeAddress(0, 5, 0, lastCol));
        for (int c = identityCols; c <= lastCol; c++) {
            sheet.setColumnWidth(c, (int) (dayWidth * 256));
        }
    }

    private void mergeTitle(Sheet sheet, int rowIndex, int lastCol, String text, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        setStyled(row, 0, text, style);
        if (lastCol > 0) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastCol));
        }
        row.setHeightInPoints(18);
    }

    private void setStyled(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value == null || "".equals(value)) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static String upper(String value) {
        return value.toUpperCase(TR);
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle note;
        final CellStyle header;
        final CellStyle dayHead;
        final CellStyle center;
        final CellStyle name;
        final CellStyle iban;
        final CellStyle mark;
        final CellStyle hours;

        Styles(Workbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);
            title = base(workbook, titleFont, HorizontalAlignment.CENTER, false);

            Font subFont = workbook.createFont();
            subFont.setBold(true);
            subFont.setFontHeightInPoints((short) 11);
            subtitle = base(workbook, subFont, HorizontalAlignment.CENTER, false);

            Font noteFont = workbook.createFont();
            noteFont.setFontHeightInPoints((short) 9);
            note = base(workbook, noteFont, HorizontalAlignment.CENTER, false);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 8);
            header = filled(workbook, headerFont);
            dayHead = filled(workbook, headerFont);
            dayHead.setWrapText(true);

            Font small = workbook.createFont();
            small.setFontHeightInPoints((short) 8);
            center = bordered(workbook, small, HorizontalAlignment.CENTER);
            name = bordered(workbook, small, HorizontalAlignment.LEFT);
            iban = bordered(workbook, small, HorizontalAlignment.LEFT);

            Font markFont = workbook.createFont();
            markFont.setBold(true);
            markFont.setFontHeightInPoints((short) 9);
            mark = filled(workbook, markFont);
            mark.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());

            hours = bordered(workbook, small, HorizontalAlignment.CENTER);
            hours.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
        }

        private static CellStyle base(Workbook workbook, Font font, HorizontalAlignment align, boolean border) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setAlignment(align);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            if (border) {
                applyBorder(style);
            }
            return style;
        }

        private static CellStyle bordered(Workbook workbook, Font font, HorizontalAlignment align) {
            return base(workbook, font, align, true);
        }

        private static CellStyle filled(Workbook workbook, Font font) {
            CellStyle style = bordered(workbook, font, HorizontalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static void applyBorder(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
