package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record SozlesmeImzaBekleyenResponse(
        Long basvuruId,
        String ogrenciNo,
        String tcKimlikNo,
        String adSoyad,
        String fakulte,
        String program,
        boolean imzaBildirimiOkundu,
        boolean imzaBildirimiEpostaGonderildi,
        Instant imzaBildirimiGonderimTarihi
) {
}
