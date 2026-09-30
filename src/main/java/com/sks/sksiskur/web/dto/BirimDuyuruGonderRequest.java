package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BirimDuyuruGonderRequest(
        @NotBlank @Size(max = 200) String baslik,
        @NotBlank @Size(max = 4000) String mesaj,
        boolean tumBirimler,
        List<@NotBlank @Size(max = 80) String> birimKodlari
) {
}
