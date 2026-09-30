package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record YoneticiPanosuDuyuruKaydetRequest(
        @NotBlank @Size(max = 200) String baslik,
        @NotBlank @Size(max = 4000) String mesaj,
        boolean aktif
) {
}
