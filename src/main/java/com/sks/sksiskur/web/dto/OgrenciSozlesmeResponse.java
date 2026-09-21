package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.AgreementType;

import java.time.Instant;

public record OgrenciSozlesmeResponse(
        AgreementType tur,
        String baslik,
        String icerik,
        int versiyon,
        boolean kabulEdildi,
        Instant kabulTarihi
) {
}
