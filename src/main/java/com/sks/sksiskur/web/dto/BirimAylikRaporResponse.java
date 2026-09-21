package com.sks.sksiskur.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BirimAylikRaporResponse(
        int yil,
        int ay,
        String birimAdi,
        BigDecimal gunlukSaat,
        List<RaporOgrenci> ogrenciler
) {
    public record RaporOgrenci(
            int siraNo,
            Long basvuruId,
            String ogrenciNo,
            String tcKimlikNo,
            String ad,
            String soyad,
            String iban,
            List<LocalDate> ekuantGunler,
            List<LocalDate> geldiGunler,
            int toplamGun,
            BigDecimal toplamSaat
    ) {
    }
}
