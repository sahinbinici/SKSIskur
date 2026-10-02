package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.TakipStatus;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IskurAylikRaporExportServiceTest {

    @Test
    void fillsKatilimciWhenStudentsAreApproved() throws Exception {
        IskurAylikRaporExportService service = new IskurAylikRaporExportService(null);
        BirimAylikRaporResponse.RaporOgrenci student = new BirimAylikRaporResponse.RaporOgrenci(
                1, 4L, "99010004", "00099010004", "Fatma", "Şahin", "TR000",
                List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)),
                List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)),
                List.of(), List.of(), List.of(), 2, BigDecimal.TEN, TakipStatus.APPROVED
        );
        var method = IskurAylikRaporExportService.class.getDeclaredMethod(
                "buildWorkbook", List.class, YearMonth.class);
        method.setAccessible(true);
        byte[] bytes = (byte[]) method.invoke(service, List.of(student), YearMonth.of(2026, 10));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sks = workbook.getSheetAt(0);
            assertTrue(sks.getSheetName().contains("SKS"), sks.getSheetName());
            Row row = sks.getRow(1);
            String name = row.getCell(1).getStringCellValue();
            assertTrue(name.toUpperCase(java.util.Locale.ROOT).contains("FATMA")
                    || name.contains("FATMA"), name);
            assertEquals("00099010004", row.getCell(2).getStringCellValue());
            assertEquals(0, workbook.getActiveSheetIndex());
        }
    }
}
