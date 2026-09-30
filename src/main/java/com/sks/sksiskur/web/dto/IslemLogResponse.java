package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;

import java.time.Instant;

public record IslemLogResponse(
        Long id,
        Instant zaman,
        Role rol,
        IslemTuru tur,
        String kullaniciAdi,
        String adSoyad,
        String varlikTipi,
        Long varlikId,
        String aciklama,
        String detay
) {
}
