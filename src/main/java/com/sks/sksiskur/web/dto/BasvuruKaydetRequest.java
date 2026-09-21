package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.Size;
public record BasvuruKaydetRequest(
        String iban,
        @Size(max = 120, message = "Hesap sahibi en fazla 120 karakter olabilir")
        String hesapSahibi
) {
}
