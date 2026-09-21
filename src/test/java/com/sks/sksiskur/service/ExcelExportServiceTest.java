package com.sks.sksiskur.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelExportServiceTest {

    private final ExcelExportService service = new ExcelExportService();

    @Test
    void createsWorkbookWithHeadersAndRows() throws Exception {
        byte[] bytes = service.singleSheet(
                "Test",
                List.of("Ad", "Sayi"),
                List.of(List.of("Ali", 3), List.of("Ayse", 5))
        );

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Test", sheet.getSheetName());
            Row header = sheet.getRow(0);
            assertEquals("Ad", header.getCell(0).getStringCellValue());
            assertEquals("Sayi", header.getCell(1).getStringCellValue());
            assertEquals("Ali", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(3.0, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertTrue(bytes.length > 100);
        }
    }
}
