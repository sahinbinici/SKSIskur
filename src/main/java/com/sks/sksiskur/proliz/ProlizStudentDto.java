package com.sks.sksiskur.proliz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProlizStudentDto(
        @JsonProperty("TC_KIMLIK_NO") String tcKimlikNo,
        @JsonProperty("OGRENCI_NO") String ogrenciNo,
        @JsonProperty("AD") String ad,
        @JsonProperty("SOYAD") String soyad,
        @JsonProperty("UYRUK") String uyruk,
        @JsonProperty("DOGUM_YERI") String dogumYeri,
        @JsonProperty("DOGUM_TARIHI") String dogumTarihi,
        @JsonProperty("CINSIYET") String cinsiyet,
        @JsonProperty("EGITIM_DERECESI") String egitimDerecesi,
        @JsonProperty("KAYIT_TARIHI") String kayitTarihi,
        @JsonProperty("OGRENIM_DURUMU") String ogrenimDurumu,
        @JsonProperty("DANISMAN_UNVAN") String danismanUnvan,
        @JsonProperty("DANISMAN_AD") String danismanAd,
        @JsonProperty("DANISMAN_SOYAD") String danismanSoyad,
        @JsonProperty("FAK_KOD") String fakulte,
        @JsonProperty("BOLUM_AD") String bolum,
        @JsonProperty("PROGRAM_AD") String program,
        @JsonProperty("DURUMU") String durumu,
        @JsonProperty("SINIF") String sinif,
        @JsonProperty("EPOSTA1") String eposta,
        @JsonProperty("GSM1") String gsm,
        @JsonProperty("OGR_ADRES") String adres,
        @JsonProperty("OGR_ADRES_IL") String il,
        @JsonProperty("OGR_ADRES_ILCE") String ilce,
        @JsonProperty("FOTO_URL") String fotoUrl
) {
}
