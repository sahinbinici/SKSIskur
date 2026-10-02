package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BasvuruDalgaCreateRequest(
        @NotNull LocalDate ogrenciBaslangicTarihi,
        @NotNull LocalDate ogrenciBitisTarihi
) {
}
