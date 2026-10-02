package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SozlesmeImzaPasifTopluRequest(
        @NotEmpty List<Long> basvuruIds
) {
}
