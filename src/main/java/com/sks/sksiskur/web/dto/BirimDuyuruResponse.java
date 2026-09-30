package com.sks.sksiskur.web.dto;

import java.time.Instant;
import java.util.List;

public record BirimDuyuruResponse(
        Long id,
        String baslik,
        String mesaj,
        String gonderenAdmin,
        Instant gonderimTarihi,
        boolean tumBirimler,
        int hedefSayisi,
        int okunanSayisi,
        List<String> birimKodlari
) {
}
