package com.sks.sksiskur.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IskurExcelParserTest {

    private final IskurExcelParser parser = new IskurExcelParser();

    @Test
    void normalizeTcAcceptsElevenDigits() {
        assertEquals("12345678901", IskurExcelParser.normalizeTc("12345678901"));
        assertEquals("12345678901", IskurExcelParser.normalizeTc("123 456 789 01"));
        assertNull(IskurExcelParser.normalizeTc("12345"));
        assertNull(IskurExcelParser.normalizeTc(""));
    }

    @Test
    void personKeyIgnoresTurkishCaseAndDiacritics() {
        assertEquals(
                IskurExcelParser.personKey("Ayşe", "Demir"),
                IskurExcelParser.personKey("AYSE", "demir")
        );
    }

    @Test
    void splitIdentityReadsCombinedNameAndLeadingTc() {
        IskurExcelParser.NameParts parts = IskurExcelParser.splitIdentity("37867381812 İNCİ DURMUŞ");
        assertEquals("37867381812", parts.tcKimlikNo());
        assertEquals("İNCİ", parts.ad());
        assertEquals("DURMUŞ", parts.soyad());
    }

    @Test
    void splitIdentityKeepsCompoundFirstName() {
        IskurExcelParser.NameParts parts = IskurExcelParser.splitIdentity("MERVE GÜL KÜLEK");
        assertNull(parts.tcKimlikNo());
        assertEquals("MERVE GÜL", parts.ad());
        assertEquals("KÜLEK", parts.soyad());
    }

    @Test
    void parseIskurBasvuruListesiWithCombinedNameColumn() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row title = sheet.createRow(2);
            title.createCell(0).setCellValue("TÜRKİYE İŞ KURUMU");
            Row header = sheet.createRow(6);
            header.createCell(0).setCellValue("SIRA");
            header.createCell(2).setCellValue("T.C. KİMLİK NO");
            header.createCell(3).setCellValue("ADI SOYADI");
            Row row = sheet.createRow(7);
            row.createCell(0).setCellValue(1);
            row.createCell(2).setCellValue("11072270000");
            row.createCell(3).setCellValue("ESMAGÜL DEMİR");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        var rows = parser.parse(new MockMultipartFile("file", "basvuru.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
        assertEquals(1, rows.size());
        assertEquals("11072270000", rows.getFirst().tcKimlikNo());
        assertEquals("ESMAGÜL", rows.getFirst().ad());
        assertEquals("DEMİR", rows.getFirst().soyad());
    }

    @Test
    void parseKesinKuraListesiSkipsYedekAndReadsTcFromName() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(6);
            header.createCell(0).setCellValue("No");
            header.createCell(4).setCellValue("Katılımcının Adı Soyadı");
            header.createCell(8).setCellValue("İkramiye");
            Row asil = sheet.createRow(8);
            asil.createCell(0).setCellValue(1);
            asil.createCell(4).setCellValue("37867381812 İNCİ DURMUŞ");
            asil.createCell(7).setCellValue("ASIL");
            Row yedek = sheet.createRow(10);
            yedek.createCell(0).setCellValue(2);
            yedek.createCell(4).setCellValue("11918248262 MERVE GÜL KÜLEK");
            yedek.createCell(7).setCellValue("YEDEK");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        var rows = parser.parse(new MockMultipartFile("file", "kesin.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
        assertEquals(1, rows.size());
        assertEquals("37867381812", rows.getFirst().tcKimlikNo());
        assertEquals("İNCİ", rows.getFirst().ad());
        assertEquals("DURMUŞ", rows.getFirst().soyad());
    }

    @Test
    @EnabledIf("officialFilesExist")
    void parseOfficialApplicationWorkbook() throws Exception {
        Path path = Path.of("C:/Users/cdikici/Documents/7 - 2025 merkez (1).xlsx");
        byte[] bytes = Files.readAllBytes(path);
        var rows = parser.parse(new MockMultipartFile("file", path.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
        assertTrue(rows.size() >= 100, "Başvuru listesi beklenenden kısa: " + rows.size());
        assertTrue(rows.stream().allMatch(row -> row.ad() != null && row.soyad() != null));
        assertTrue(rows.stream().filter(row -> row.tcKimlikNo() != null).count() > 50);
    }

    @Test
    @EnabledIf("officialFilesExist")
    void parseOfficialKesinWorkbook() throws Exception {
        Path path = Path.of("C:/Users/cdikici/Documents/ASİL LİSTE merkez kampüs sonuc.xls");
        byte[] bytes = Files.readAllBytes(path);
        var rows = parser.parse(new MockMultipartFile("file", path.getFileName().toString(),
                "application/vnd.ms-excel", bytes));
        assertTrue(rows.size() >= 100, "Kesin liste beklenenden kısa: " + rows.size());
        assertTrue(rows.stream().allMatch(row -> row.tcKimlikNo() != null && row.tcKimlikNo().length() == 11));
    }

    static boolean officialFilesExist() {
        return Files.exists(Path.of("C:/Users/cdikici/Documents/7 - 2025 merkez (1).xlsx"))
                && Files.exists(Path.of("C:/Users/cdikici/Documents/ASİL LİSTE merkez kampüs sonuc.xls"));
    }
}
