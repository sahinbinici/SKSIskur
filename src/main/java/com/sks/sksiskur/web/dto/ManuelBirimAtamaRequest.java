package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ManuelBirimAtamaRequest(
        @NotBlank(message = "Hedef birim zorunludur") String birimKodu
) { }
