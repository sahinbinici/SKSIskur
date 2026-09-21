package com.sks.sksiskur.web.dto;

public record BirimOgrenciResponse(
        Long basvuruId,
        String ogrenciNo,
        String adSoyad,
        String fakulte,
        String bolum,
        String program,
        String eposta,
        String gsm,
        int kullanilanIzinGunu,
        int izinGunLimiti,
        int kalanIzinGunu
) {
}
