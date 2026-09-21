package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.BelgeDogrulamaDurumu;
import com.sks.sksiskur.domain.KayitTuru;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

public record BasvuruResponse(
        Long id,
        ApplicationStatus status,
        KayitTuru kayitTuru,
        Instant kayitTarihi,
        String iban,
        String hesapSahibi,
        BigDecimal aylikGelir,
        String adminNotu,
        String inceleyenAdmin,
        String atananAdmin,
        Instant olusturmaTarihi,
        Instant guncellemeTarihi,
        Instant gonderimTarihi,
        Instant incelemeTarihi,
        boolean locked,
        String atananBirimKodu,
        String atananBirimAdi,
        String atamaTuru,
        Instant atamaTarihi,
        Long basvuruDonemiId,
        String basvuruDonemiAdi,
        boolean basvuruDonemiAktif,
        StudentProfileResponse student,
        List<BelgeResponse> belgeler
) {
    public record BelgeResponse(
            Long id,
            DocumentType belgeTipi,
            String belgeAdi,
            String haneUyesiAdi,
            String orijinalAd,
            String icerikTipi,
            Long boyutByte,
            BelgeDogrulamaDurumu dogrulamaDurumu,
            Instant yuklemeTarihi
    ) {
    }
}
