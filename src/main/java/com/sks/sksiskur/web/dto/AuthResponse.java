package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.Role;

public record AuthResponse(
        String token,
        Role role,
        String displayName,
        String ogrenciNo,
        String birimKodu,
        String birimAdi
) {
}
