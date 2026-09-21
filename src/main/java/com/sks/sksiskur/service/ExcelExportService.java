package com.sks.sksiskur.service;

import com.sks.sksiskur.exception.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ExcelExportService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"));
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR"));

    public record SheetSpec(String name, List<String> headers, List<List<Object>> rows) {
    }

    public byte[] workbook(List<SheetSpec> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Excel için veri bulunamadı.");
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            for (int i = 0; i < sheets.size(); i++) {
                SheetSpec spec = sheets.get(i);
                Sheet sheet = workbook.createSheet(safeSheetName(spec.name(), i));
                writeHeader(sheet, spec.headers(), headerStyle);
                writeRows(sheet, spec.rows());
                autosize(sheet, spec.headers().size());
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Excel dosyası oluşturulamadı.");
        }
    }

    public byte[] singleSheet(String sheetName, List<String> headers, List<List<Object>> rows) {
        return workbook(List.of(new SheetSpec(sheetName, headers, rows)));
    }

    private void writeHeader(Sheet sheet, List<String> headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
    }

    private void writeRows(Sheet sheet, List<List<Object>> rows) {
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<Object> values = rows.get(rowIndex);
            for (int col = 0; col < values.size(); col++) {
                setCellValue(row.createCell(col), values.get(col));
            }
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(DATE.format(date));
            return;
        }
        if (value instanceof Instant instant) {
            cell.setCellValue(DATE_TIME.format(LocalDateTime.ofInstant(instant, ZONE)));
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void autosize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(width + 512, 12000));
        }
    }

    private String safeSheetName(String name, int index) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            trimmed = "Sayfa" + (index + 1);
        }
        return trimmed.replaceAll("[\\\\/*?:\\[\\]]", " ").substring(0, Math.min(trimmed.length(), 31));
    }
}
