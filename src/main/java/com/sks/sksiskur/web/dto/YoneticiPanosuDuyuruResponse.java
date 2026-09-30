package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record YoneticiPanosuDuyuruResponse(
        Long id,
        String baslik,
        String mesaj,
        boolean aktif,
        String gonderenAdmin,
        Instant olusturmaTarihi,
        Instant guncellemeTarihi
) {
}
