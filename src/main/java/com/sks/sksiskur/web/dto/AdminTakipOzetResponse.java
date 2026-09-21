package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.TakipStatus;

import java.util.List;

public record AdminTakipOzetResponse(
        int yil,
        int ay,
        String birimKodu,
        String birimAdi,
        List<Satir> ogrenciler
) {
    public record Satir(
            Long basvuruId,
            String ogrenciNo,
            String adSoyad,
            String birimKodu,
            String birimAdi,
            int ekuant,
            int geldi,
            int gelmedi,
            int izinli,
            int raporlu,
            TakipStatus status,
            boolean locked
    ) {
    }
}
