package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.Size;

public record BasvuruKaydetRequest(
        String iban,
        @Size(max = 120, message = "Hesap sahibi en fazla 120 karakter olabilir")
        String hesapSahibi,
        @Size(max = 4, message = "Banka şube kodu en fazla 4 karakter olabilir")
        String bankaSubeKodu,
        @Size(max = 16, message = "Hesap numarası en fazla 16 karakter olabilir")
        String hesapNumarasi,
        @Size(max = 160, message = "E-posta en fazla 160 karakter olabilir")
        String eposta,
        @Size(max = 20, message = "Telefon en fazla 20 karakter olabilir")
        String gsm
) {
}
