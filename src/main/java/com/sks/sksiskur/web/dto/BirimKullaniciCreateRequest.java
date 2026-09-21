package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BirimKullaniciCreateRequest(
        @NotBlank(message = "Kullanıcı adı zorunludur")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,60}", message = "Kullanıcı adı harf, rakam, nokta, tire veya alt çizgi olabilir")
        String username,
        @NotBlank(message = "Şifre zorunludur")
        @Size(min = 6, max = 80, message = "Şifre en az 6 karakter olmalıdır")
        String password,
        @NotBlank(message = "Ad soyad zorunludur")
        @Size(max = 120, message = "Ad soyad 120 karakteri aşamaz")
        String adSoyad,
        @NotBlank(message = "Birim seçiniz")
        @Size(max = 32, message = "Birim kodu geçersiz")
        String birimKodu
) {
}
