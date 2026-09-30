package com.sks.sksiskur.web.dto;

import java.time.Instant;

public record ImzaBildirimiGonderResponse(
        int hedefOgrenci,
        int sayfaBildirimi,
        int epostaGonderilen,
        int epostaAtlanan,
        int epostaBasarisiz,
        Instant gonderimTarihi,
        String gonderenAdmin
) {
}
