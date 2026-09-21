package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.PuantajDurum;

import java.time.LocalDate;

public record IzinRaporOgrenciResponse(
        Long basvuruId,
        String ogrenciNo,
        String adSoyad,
        String birimAdi,
        LocalDate tarih,
        PuantajDurum durum,
        boolean belgeVar,
        String belgeAdi
) {
}
