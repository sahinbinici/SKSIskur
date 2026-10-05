package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.TakipStatus;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CetvelExcelExportServiceTest {

    private final CetvelExcelExportService service = new CetvelExcelExportService();

    @Test
    void writesOfficialEkuantAndPuantajGrids() throws Exception {
        BirimAylikRaporResponse rapor = new BirimAylikRaporResponse(
                2026,
                10,
                "Gaziantep Eğitim Fakültesi",
                new BigDecimal("7.5"),
                true,
                false,
                1,
                0,
                0,
                0,
                List.of(new BirimAylikRaporResponse.RaporOgrenci(
                        1,
                        10L,
                        "99010010",
                        "11111111111",
                        "Ayşe",
                        "Yılmaz",
                        "TR310001200133800004000406",
                        List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)),
                        List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)),
                        List.of(),
                        List.of(),
                        List.of(),
                        2,
                        new BigDecimal("15.0"),
                        TakipStatus.SUBMITTED
                ))
        );

        byte[] bytes = service.workbook(rapor);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet ekuant = workbook.getSheet("EK-6");
            Sheet puantaj = workbook.getSheet("Puantaj");
            assertEquals("EK-6: İŞKUR Gençlik Programı Katılımcı Gün Çizelgesi",
                    ekuant.getRow(0).getCell(0).getStringCellValue());
            assertEquals("1. HAFTA", ekuant.getRow(4).getCell(3).getStringCellValue());
            assertEquals("AYŞE YILMAZ", ekuant.getRow(6).getCell(2).getStringCellValue());
            assertTrue(ekuant.getRow(5).getCell(3).getStringCellValue().contains("Pazartesi"));

            boolean hasX = false;
            for (int col = 3; col < 40; col++) {
                var cell = ekuant.getRow(6).getCell(col);
                if (cell != null && "X".equals(cell.getStringCellValue())) {
                    hasX = true;
                    break;
                }
            }
            assertTrue(hasX);

            assertEquals("T.C. GAZİANTEP ÜNİVERSİTESİ", puantaj.getRow(0).getCell(0).getStringCellValue());
            assertEquals("ÇALIŞTIĞI GÜNLER", puantaj.getRow(3).getCell(5).getStringCellValue());
            assertEquals(5.0, puantaj.getRow(4).getCell(9).getNumericCellValue());
            assertEquals("TR310001200133800004000406", puantaj.getRow(5).getCell(4).getStringCellValue());
            assertEquals(7.5, puantaj.getRow(5).getCell(9).getNumericCellValue());
            assertEquals(15.0, puantaj.getRow(5).getCell(36).getNumericCellValue());
            assertEquals(2.0, puantaj.getRow(5).getCell(37).getNumericCellValue());
        }
    }
}
