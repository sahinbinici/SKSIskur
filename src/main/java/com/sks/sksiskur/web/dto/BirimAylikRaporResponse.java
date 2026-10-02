package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.TakipStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BirimAylikRaporResponse(
        int yil,
        int ay,
        String birimAdi,
        BigDecimal gunlukSaat,
        boolean yazdirilabilir,
        int onayliOgrenci,
        int onayBekleyenOgrenci,
        int gonderilmeyenOgrenci,
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
            List<LocalDate> gelmediGunler,
            List<LocalDate> izinliGunler,
            List<LocalDate> raporluGunler,
            int toplamGun,
            BigDecimal toplamSaat,
            TakipStatus status
    ) {
    }
}
