package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record IskurListeUploadResponse(
        Long donemId,
        long kayitSayisi,
        long atlananTekrar,
        Instant yuklemeTarihi,
        String yukleyenAdmin
) {
}
