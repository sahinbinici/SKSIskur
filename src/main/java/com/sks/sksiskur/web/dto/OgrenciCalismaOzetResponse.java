package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.TakipStatus;

public record OgrenciCalismaOzetResponse(
        Long basvuruId,
        String birimAdi,
        String birimKodu,
        String donemAdi,
        int yil,
        int ay,
        int kullanilanIzinGunu,
        int izinGunLimiti,
        int kalanIzinGunu,
        int buAyTamGun,
        int buAyEkuantGun,
        TakipStatus takipDurumu,
        boolean takipKilitli
) {
}
