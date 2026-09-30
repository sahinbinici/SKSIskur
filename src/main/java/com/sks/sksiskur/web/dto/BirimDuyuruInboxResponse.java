package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record BirimDuyuruInboxResponse(
        Long id,
        String baslik,
        String mesaj,
        String gonderenAdmin,
        Instant gonderimTarihi,
        boolean okundu
) {
}
