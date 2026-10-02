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

    public record NameParts(String ad, String soyad, String tcKimlikNo) {
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
            if (!hasNameColumns(columns)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Excel dosyasında Ad/Soyad veya Adı Soyadı sütunu bulunamadı. İŞKUR listesindeki başlıkları kontrol edin.");
            }

            List<ParsedRow> rows = new ArrayList<>();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row) || isYedekRow(row)) {
                    continue;
                }
                NameParts names = resolveNames(row, columns);
                if (names == null) {
                    continue;
                }
                if (names.ad().isBlank() || names.soyad().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            (i + 1) + ". satırda ad ve soyad birlikte dolu olmalıdır.");
                }
                String tc = firstNonBlank(normalizeTc(cell(row, columns.get(Column.TC))), names.tcKimlikNo());
                String ogrenciNo = normalizeOgrenciNo(cell(row, columns.get(Column.OGRENCi_NO)));
                rows.add(new ParsedRow(tc, names.ad(), names.soyad(), ogrenciNo));
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
        TC, AD, SOYAD, AD_SOYAD, OGRENCi_NO
    }

    private int findHeaderRow(Sheet sheet) {
        for (int i = sheet.getFirstRowNum(); i <= Math.min(sheet.getLastRowNum(), 15); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Map<Column, Integer> columns = mapColumns(row);
            if (hasNameColumns(columns)) {
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
            } else if (matches(header, Set.of(
                    "adisoyadi", "adisoyad", "adsoyad", "advesoyad",
                    "katilimcininadisoyadi", "ogrenciadisoyadi"
            ))) {
                columns.putIfAbsent(Column.AD_SOYAD, cell.getColumnIndex());
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

    private static boolean hasNameColumns(Map<Column, Integer> columns) {
        return columns.containsKey(Column.AD_SOYAD)
                || (columns.containsKey(Column.AD) && columns.containsKey(Column.SOYAD));
    }

    private NameParts resolveNames(Row row, Map<Column, Integer> columns) {
        if (columns.containsKey(Column.AD_SOYAD)) {
            String raw = cell(row, columns.get(Column.AD_SOYAD));
            if (raw.isBlank()) {
                return null;
            }
            return splitIdentity(raw);
        }
        String ad = cell(row, columns.get(Column.AD));
        String soyad = cell(row, columns.get(Column.SOYAD));
        if (ad.isBlank() && soyad.isBlank()) {
            return null;
        }
        NameParts fromAd = splitIdentity(ad);
        if (soyad.isBlank()) {
            return fromAd;
        }
        return new NameParts(fromAd.ad().isBlank() ? ad.trim() : fromAd.ad(), soyad.trim(), fromAd.tcKimlikNo());
    }

    static NameParts splitIdentity(String raw) {
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (text.isBlank()) {
            return new NameParts("", "", null);
        }
        String tc = null;
        int space = text.indexOf(' ');
        if (space > 0) {
            String maybeTc = normalizeTc(text.substring(0, space));
            if (maybeTc != null) {
                tc = maybeTc;
                text = text.substring(space + 1).trim();
            }
        }
        int lastSpace = text.lastIndexOf(' ');
        if (lastSpace <= 0) {
            return new NameParts(text, "", tc);
        }
        return new NameParts(text.substring(0, lastSpace).trim(), text.substring(lastSpace + 1).trim(), tc);
    }

    private boolean isYedekRow(Row row) {
        for (Cell cell : row) {
            String value = NameNormalizer.fold(FORMATTER.formatCellValue(cell)).replace(" ", "");
            if ("yedek".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
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

    public static String personKey(String ad, String soyad) {
        return NameNormalizer.fold(ad + " " + soyad).trim();
    }
}
