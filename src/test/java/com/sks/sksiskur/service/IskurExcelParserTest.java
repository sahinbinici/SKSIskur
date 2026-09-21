package com.sks.sksiskur.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IskurExcelParserTest {

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
}
