package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SozlesmeKaydetRequest(
        @NotBlank(message = "Sözleşme başlığı zorunludur.")
        @Size(max = 180, message = "Sözleşme başlığı en fazla 180 karakter olabilir.")
        String baslik,
        @NotBlank(message = "Sözleşme içeriği zorunludur.")
        @Size(max = 50000, message = "Sözleşme içeriği en fazla 50.000 karakter olabilir.")
        String icerik
) {
}
