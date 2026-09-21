package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.Min;

public record DagitimBirimiUpdateRequest(
        @Min(value = 0, message = "Kontenjan negatif olamaz") int kontenjan,
        boolean dagitimaAcik
) { }
