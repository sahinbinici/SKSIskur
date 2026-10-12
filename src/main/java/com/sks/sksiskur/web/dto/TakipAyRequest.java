package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.TakipCetvelTuru;
import jakarta.validation.constraints.NotNull;

public record TakipAyRequest(
        int yil,
        int ay,
        @NotNull TakipCetvelTuru cetvel
) {
}
