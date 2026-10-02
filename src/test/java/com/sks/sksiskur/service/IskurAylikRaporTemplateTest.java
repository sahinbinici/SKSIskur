package com.sks.sksiskur.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class IskurAylikRaporTemplateTest {

    @Test
    void officialTemplateHasKatilimciAndDevamsizlikSheets() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/templates/iskur-aylik-bordro-sablonu.xlsm")) {
            assertNotNull(input, "Bordro şablonu bulunamadı.");
            try (Workbook workbook = new XSSFWorkbook(input)) {
                assertNotNull(workbook.getSheet("!!!KATILIMCI LİSTESİ!!!"));
                assertNotNull(workbook.getSheet("1-Devamsızlık Formu (Ek-4)"));
                assertNotNull(workbook.getSheet("6-Devam Gün Çizelgesi"));
            }
        }
    }
}
