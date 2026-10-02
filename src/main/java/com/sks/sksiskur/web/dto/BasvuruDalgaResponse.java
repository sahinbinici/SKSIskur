package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.BasvuruDalgaDurum;

import java.time.Instant;
import java.time.LocalDate;

public record BasvuruDalgaResponse(
        Long id,
        Long basvuruDonemiId,
        int turNo,
        String ad,
        BasvuruDalgaDurum durum,
        boolean aktif,
        LocalDate ogrenciBaslangicTarihi,
        LocalDate ogrenciBitisTarihi,
        boolean ogrenciGirisiAcik,
        boolean kesinOnaylandi,
        boolean imzaBildirimiGonderildi,
        long iskurListeKayitSayisi,
        long kesinListeKayitSayisi,
        Instant olusturmaTarihi
) {
}
