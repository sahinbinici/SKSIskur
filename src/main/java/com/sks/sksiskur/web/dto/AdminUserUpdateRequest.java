package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUserUpdateRequest(
        @NotBlank(message = "Kullanıcı adı zorunludur")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,60}", message = "Kullanıcı adı harf, rakam, nokta, tire veya alt çizgi olabilir")
        String username,
        String password,
        @NotBlank(message = "Ad soyad zorunludur")
        @Size(max = 120, message = "Ad soyad 120 karakteri aşamaz")
        String adSoyad,
        @NotNull(message = "Durum zorunludur")
        Boolean aktif
) {
}
