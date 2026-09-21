package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.KayitTuru;

import java.time.Instant;
import java.util.List;

public record KayitListesiResponse(
        boolean kesinOnaylandi,
        Instant onayTarihi,
        String onaylayanAdmin,
        long bekleyen,
        long kesin,
        long yedek,
        boolean dagitimAcik,
        List<Satir> ogrenciler
) {
    public record Satir(
            Long basvuruId,
            String ogrenciNo,
            String tcKimlikNo,
            String ad,
            String soyad,
            String adSoyad,
            String fakulte,
            String program,
            String bolum,
            KayitTuru kayitTuru,
            String atananBirimAdi
    ) {
    }
}
