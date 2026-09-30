package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.AdminRole;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String username,
        String adSoyad,
        boolean aktif,
        AdminRole rol,
        long bekleyenBasvuruSayisi,
        Instant olusturmaTarihi,
        boolean benimHesabim
) {
}
