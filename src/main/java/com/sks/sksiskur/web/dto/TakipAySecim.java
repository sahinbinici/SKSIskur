package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TakipAySecim(
        @Min(2000) @Max(2100) int yil,
        @Min(1) @Max(12) int ay
) {
}
