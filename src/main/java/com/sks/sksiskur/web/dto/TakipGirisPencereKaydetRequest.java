package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.TakipGirisKapsam;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TakipGirisPencereKaydetRequest(
        @NotNull TakipGirisKapsam kapsam,
        LocalDate ekuantBaslangic,
        LocalDate ekuantBitis,
        LocalDate puantajBaslangic,
        LocalDate puantajBitis
) {
}
