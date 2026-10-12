package com.sks.sksiskur.web.dto;

import java.time.LocalDate;

public record TakipGirisPencereResponse(
        int yil,
        int ay,
        LocalDate ekuantBaslangic,
        LocalDate ekuantBitis,
        boolean ekuantAcik,
        String ekuantKaynak,
        LocalDate puantajBaslangic,
        LocalDate puantajBitis,
        boolean puantajAcik,
        String puantajKaynak,
        LocalDate ayEkuantBaslangic,
        LocalDate ayEkuantBitis,
        LocalDate ayPuantajBaslangic,
        LocalDate ayPuantajBitis,
        LocalDate donemEkuantBaslangic,
        LocalDate donemEkuantBitis,
        LocalDate donemPuantajBaslangic,
        LocalDate donemPuantajBitis
) {
}
