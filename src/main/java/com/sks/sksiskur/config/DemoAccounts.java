package com.sks.sksiskur.config;

import com.sks.sksiskur.sicil.WorkUnit;

import java.util.List;
import java.util.Optional;

public final class DemoAccounts {

    public static final String STUDENT_PASSWORD = "ogrenci123";
    public static final String UNIT_PASSWORD = "birim123";

    private DemoAccounts() {
    }

    public static List<WorkUnit> units() {
        return List.of(
                new WorkUnit("4", "Sağlık Kültür ve Spor Daire Başkanlığı", "SAĞLIK KÜLTÜR ve SPOR D.BŞK.", 1649, "DEMO"),
                new WorkUnit("6", "Kütüphane Dökümantasyon Daire Başkanlığı", "KÜTÜPHANE DÖKÜMANTASYON D.BŞK.", 1650, "DEMO"),
                new WorkUnit("35", "Gaziantep Eğitim Fakültesi", "GAZİANTEP EĞİTİM FAKÜLTESİ", 1660, "DEMO"),
                new WorkUnit("155", "Teknik Bilimler MYO Müdürlüğü", "TEKNİK BİLİMLER MYO", 6273, "DEMO")
        );
    }

    public static Optional<WorkUnit> authenticate(String birimKodu, String sifre) {
        if (birimKodu == null || sifre == null || !UNIT_PASSWORD.equals(sifre)) {
            return Optional.empty();
        }
        String kod = birimKodu.trim();
        return units().stream().filter(unit -> unit.kod().equals(kod)).findFirst();
    }
}
