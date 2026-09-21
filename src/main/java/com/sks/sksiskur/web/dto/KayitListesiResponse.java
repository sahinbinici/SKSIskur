package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.KayitTuru;

import java.time.Instant;
import java.util.List;

public record KayitListesiResponse(
        boolean kesinListeYuklendi,
        Instant kesinListeYuklemeTarihi,
        String kesinListeYukleyenAdmin,
        long onayliBasvuru,
        long kesinListede,
        long kesinListedeDegil,
        long kesinKarsilastirmaBekleyen,
        long listedeBasvuruEslesmedi,
        boolean kesinOnaylandi,
        Instant onayTarihi,
        String onaylayanAdmin,
        boolean dagitimAcik,
        List<Satir> ogrenciler,
        List<ListedeEslesmeyen> listedeEslesmeyenler
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
            Boolean kesinListede,
            KayitTuru kayitTuru,
            String atananBirimAdi
    ) {
    }

    public record ListedeEslesmeyen(
            String tcKimlikNo,
            String ad,
            String soyad,
            String ogrenciNo
    ) {
    }
}
