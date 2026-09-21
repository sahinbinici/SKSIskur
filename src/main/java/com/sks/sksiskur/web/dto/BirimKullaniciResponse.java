package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record BirimKullaniciResponse(
        Long id,
        String username,
        String adSoyad,
        String birimKodu,
        String birimAdi,
        boolean aktif,
        Instant olusturmaTarihi
) {
}
