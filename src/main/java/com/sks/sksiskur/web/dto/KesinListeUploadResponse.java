package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record KesinListeUploadResponse(
        Long donemId,
        long kayitSayisi,
        long atlananTekrar,
        long eslesen,
        long kesinListedeDegil,
        long listedeBasvuruEslesmedi,
        Instant yuklemeTarihi,
        String yukleyenAdmin
) {
}
