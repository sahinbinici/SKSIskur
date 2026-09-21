package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String username,
        String adSoyad,
        boolean aktif,
        long bekleyenBasvuruSayisi,
        Instant olusturmaTarihi
) {
}
