package com.sks.sksiskur.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

public record BasvuruDonemiResponse(
        Long id,
        String ad,
        boolean aktif,
        Instant olusturmaTarihi,
        Instant kapanisTarihi,
        LocalDate ogrenciBaslangicTarihi,
        LocalDate ogrenciBitisTarihi,
        boolean ogrenciGirisiAcik,
        BigDecimal aylikGelirLimiti
) {
}
