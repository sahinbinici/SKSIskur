package com.sks.sksiskur.sicil;

public record WorkUnit(
        String kod,
        String ad,
        String adUzun,
        Integer kamuKod,
        String kaynak
) {
    public String displayName() {
        if (ad != null && !ad.isBlank()) {
            return ad;
        }
        return adUzun != null ? adUzun : kod;
    }
}
