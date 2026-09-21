package com.sks.sksiskur.service;

import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.sicil.NameNormalizer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class IskurExcelParser {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DataFormatter FORMATTER = new DataFormatter(TR);

    public record ParsedRow(String tcKimlikNo, String ad, String soyad, String ogrenciNo) {
    }

    public List<ParsedRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Excel dosyası seçilmedi.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(TR);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Yalnızca .xlsx veya .xls dosyaları kabul edilir.");
        }

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Excel dosyasında sayfa bulunamadı.");
            }
            int headerRowIndex = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowIndex);
            Map<Column, Integer> columns = mapColumns(headerRow);
            if (columns.get(Column.AD) == null || columns.get(Column.SOYAD) == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Excel dosyasında Ad ve Soyad sütunları bulunamadı. İŞKUR listesindeki başlıkları kontrol edin.");
            }

            List<ParsedRow> rows = new ArrayList<>();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                String ad = cell(row, columns.get(Column.AD));
                String soyad = cell(row, columns.get(Column.SOYAD));
                if (ad.isBlank() && soyad.isBlank()) {
                    continue;
                }
                if (ad.isBlank() || soyad.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            (i + 1) + ". satırda ad ve soyad birlikte dolu olmalıdır.");
                }
                String tc = normalizeTc(cell(row, columns.get(Column.TC)));
                String ogrenciNo = normalizeOgrenciNo(cell(row, columns.get(Column.OGRENCi_NO)));
                rows.add(new ParsedRow(tc, ad.trim(), soyad.trim(), ogrenciNo));
            }
            if (rows.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Excel dosyasında geçerli öğrenci kaydı bulunamadı.");
            }
            return rows;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Excel dosyası okunamadı: " + ex.getMessage());
        }
    }

    private enum Column {
        TC, AD, SOYAD, OGRENCi_NO
    }

    private int findHeaderRow(Sheet sheet) {
        for (int i = sheet.getFirstRowNum(); i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Map<Column, Integer> columns = mapColumns(row);
            if (columns.containsKey(Column.AD) && columns.containsKey(Column.SOYAD)) {
                return i;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Excel dosyasında başlık satırı bulunamadı.");
    }

    private Map<Column, Integer> mapColumns(Row headerRow) {
        Map<Column, Integer> columns = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = normalizeHeader(FORMATTER.formatCellValue(cell));
            if (header.isBlank()) {
                continue;
            }
            if (matches(header, Set.of("tckimlikno", "tckimlik", "tckn", "tc", "kimlikno", "tckimliknumarasi"))) {
                columns.putIfAbsent(Column.TC, cell.getColumnIndex());
            } else if (matches(header, Set.of("ad", "adi", "ogrenciadi", "isim"))) {
                columns.putIfAbsent(Column.AD, cell.getColumnIndex());
            } else if (matches(header, Set.of("soyad", "soyadi", "ogrencisoyadi", "soyisim"))) {
                columns.putIfAbsent(Column.SOYAD, cell.getColumnIndex());
            } else if (matches(header, Set.of("ogrencino", "ogrno", "okulno", "numara"))) {
                columns.putIfAbsent(Column.OGRENCi_NO, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private static boolean matches(String header, Set<String> aliases) {
        return aliases.contains(header);
    }

    private static String normalizeHeader(String value) {
        return NameNormalizer.fold(value).replace(" ", "");
    }

    private static String cell(Row row, Integer index) {
        if (index == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    private static boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (!FORMATTER.formatCellValue(cell).trim().isBlank()) {
                return false;
            }
        }
        return true;
    }

    static String normalizeTc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() == 11 ? digits : null;
    }

    static String normalizeOgrenciNo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    static String personKey(String ad, String soyad) {
        return NameNormalizer.fold(ad + " " + soyad).trim();
    }
}
