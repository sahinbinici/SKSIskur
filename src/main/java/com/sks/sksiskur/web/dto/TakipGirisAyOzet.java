package com.sks.sksiskur.web.dto;

import java.time.LocalDate;

public record TakipGirisAyOzet(
        int yil,
        int ay,
        LocalDate ekuantBaslangic,
        LocalDate ekuantBitis,
        LocalDate puantajBaslangic,
        LocalDate puantajBitis
) {
}
