package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.math.BigDecimal;

public record BasvuruDonemiCreateRequest(
        @NotBlank(message = "Dönem adı zorunludur.")
        @Size(max = 120, message = "Dönem adı en fazla 120 karakter olabilir.")
        String ad,
        @NotNull(message = "Öğrenci başvuru başlangıç tarihi zorunludur.")
        LocalDate ogrenciBaslangicTarihi,
        @NotNull(message = "Öğrenci başvuru bitiş tarihi zorunludur.")
        LocalDate ogrenciBitisTarihi,
        @NotNull(message = "Aylık gelir limiti zorunludur.")
        @jakarta.validation.constraints.DecimalMin(value = "0.00", message = "Aylık gelir limiti negatif olamaz.")
        BigDecimal aylikGelirLimiti
) {
}
