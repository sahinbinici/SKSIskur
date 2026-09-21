package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank(message = "Kullanıcı adı zorunludur") String username,
        @NotBlank(message = "Şifre zorunludur") String password
) {
}
