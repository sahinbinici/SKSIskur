package com.sks.sksiskur.web.dto;

import java.time.Instant;
import java.util.List;

public record IskurListeResponse(
        Long donemId,
        String donemAd,
        boolean yuklendi,
        long kayitSayisi,
        Instant yuklemeTarihi,
        long eslesenSayisi,
        List<Satir> onizleme
) {
    public record Satir(
            String tcKimlikNo,
            String ad,
            String soyad,
            String ogrenciNo
    ) {
    }
}
