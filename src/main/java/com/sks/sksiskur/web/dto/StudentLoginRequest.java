package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;

public record StudentLoginRequest(
        @NotBlank(message = "Öğrenci numarası zorunludur") String ogrenciNo,
        @NotBlank(message = "Şifre zorunludur") String sifre
) {
}
