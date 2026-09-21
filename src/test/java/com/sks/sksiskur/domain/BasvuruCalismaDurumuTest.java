package com.sks.sksiskur.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasvuruCalismaDurumuTest {

    @Test
    void terminationTakesEffectAtTheBeginningOfTheFollowingMonth() {
        Basvuru basvuru = new Basvuru();
        basvuru.setIliskiBitisTarihi(LocalDate.of(2026, 10, 1));

        assertTrue(basvuru.canWorkIn(YearMonth.of(2026, 9)));
        assertFalse(basvuru.canWorkIn(YearMonth.of(2026, 10)));
    }
}
