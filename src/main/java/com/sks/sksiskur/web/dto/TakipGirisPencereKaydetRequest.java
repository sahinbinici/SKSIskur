package com.sks.sksiskur.web.dto;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

public record TakipGirisPencereKaydetRequest(
        @Valid List<TakipAySecim> aylar,
        LocalDate ekuantBaslangic,
        LocalDate ekuantBitis,
        LocalDate puantajBaslangic,
        LocalDate puantajBitis
) {
}
