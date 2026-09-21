package com.sks.sksiskur.web.dto;

public record AdminOzetResponse(
        long toplam,
        long taslak,
        long gonderildi,
        long onaylandi,
        long reddedildi,
        long atanan,
        long kesin,
        long yedek,
        long kayitBekleyen,
        boolean kesinListeOnayli
) {
}
