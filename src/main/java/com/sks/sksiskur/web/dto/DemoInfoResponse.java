package com.sks.sksiskur.web.dto;

import java.util.List;

public record DemoInfoResponse(
        boolean enabled,
        String adminUsername,
        String adminPassword,
        String ogrenciSifre,
        String birimSifre,
        List<Ogrenci> ogrenciler,
        List<Birim> birimler
) {
    public record Ogrenci(String ogrenciNo, String adSoyad, String durum) {
    }

    public record Birim(String kod, String ad) {
    }
}
