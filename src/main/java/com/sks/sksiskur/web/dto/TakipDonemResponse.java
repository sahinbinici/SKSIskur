package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.PuantajDurum;
import com.sks.sksiskur.domain.TakipStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TakipDonemResponse(
        Long id,
        Long basvuruId,
        int yil,
        int ay,
        TakipStatus status,
        TakipStatus ekuantStatus,
        TakipStatus puantajStatus,
        boolean locked,
        boolean ekuantLocked,
        boolean puantajLocked,
        Instant gonderimTarihi,
        List<LocalDate> ekuantGunler,
        List<PuantajGunResponse> puantaj,
        List<String> ekuantUyarilari,
        List<LocalDate> kapaliGunler,
        int ekuantKotaGunSayisi,
        int ekuantKotaGunLimiti,
        int toplamIzinGunu,
        int izinGunLimiti,
        boolean testTakipModu,
        LocalDate gelmediKesimTarihi,
        LocalDate ekuantGirisBaslangic,
        LocalDate ekuantGirisBitis,
        boolean ekuantGirisAcik,
        LocalDate puantajGirisBaslangic,
        LocalDate puantajGirisBitis,
        boolean puantajGirisAcik,
        BirimOgrenciResponse ogrenci
) {
    public record PuantajGunResponse(
            LocalDate tarih,
            PuantajDurum durum,
            String belgeAdi,
            boolean belgeVar
    ) {
    }
}
