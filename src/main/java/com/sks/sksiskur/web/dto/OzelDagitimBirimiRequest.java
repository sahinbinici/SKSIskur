package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OzelDagitimBirimiRequest(
        @NotBlank(message = "Birim kodu zorunludur") @Size(max = 40) String kod,
        @NotBlank(message = "Birim adı zorunludur") @Size(max = 200) String ad,
        @Min(value = 0, message = "Kontenjan negatif olamaz") int kontenjan,
        boolean dagitimaAcik
) { }
