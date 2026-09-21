package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.PuantajDurum;

import java.time.LocalDate;
import java.util.List;

public record PuantajKaydetRequest(int yil, int ay, List<Kayit> kayitlar) {
    public record Kayit(LocalDate tarih, PuantajDurum durum) {
    }
}
