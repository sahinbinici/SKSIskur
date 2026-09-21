package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.AgreementType;

import java.time.Instant;

public record SozlesmeResponse(
        AgreementType tur,
        String baslik,
        String icerik,
        int versiyon,
        Instant guncellemeTarihi
) {
}
