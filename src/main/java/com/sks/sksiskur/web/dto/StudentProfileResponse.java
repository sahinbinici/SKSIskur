package com.sks.sksiskur.web.dto;

public record StudentProfileResponse(
        Long id,
        String ogrenciNo,
        String tcKimlikNo,
        String ad,
        String soyad,
        String adSoyad,
        String uyruk,
        String dogumYeri,
        String dogumTarihi,
        String cinsiyet,
        String egitimDerecesi,
        String kayitTarihi,
        String ogrenimDurumu,
        String fakulte,
        String bolum,
        String program,
        String sinif,
        String durumu,
        String eposta,
        String gsm,
        String adres,
        String il,
        String ilce,
        String fotoUrl,
        String danisman,
        boolean demoOgrenci,
        boolean iskurBasvuruyaUygun,
        String iskurBasvuruEngelMesaji
) {
}
