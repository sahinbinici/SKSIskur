package com.sks.sksiskur.config;

import com.sks.sksiskur.sicil.WorkUnit;

import java.util.List;
import java.util.Optional;

public final class DemoAccounts {

    public static final String STUDENT_PASSWORD = "ogrenci123";
    public static final String UNIT_PASSWORD = "birim123";
    public static final int STUDENT_COUNT = 100;

    private static final String[] ADLAR = {
            "Ahmet", "Ayşe", "Mehmet", "Fatma", "Ali", "Elif", "Mustafa", "Zeynep", "Hasan", "Emine",
            "Hüseyin", "Hatice", "İbrahim", "Meryem", "Osman", "Esra", "Yusuf", "Seda", "Ömer", "Derya",
            "Burak", "Gül", "Serkan", "Pınar", "Emre", "Selin", "Cem", "Büşra", "Kaan", "Melis"
    };

    private static final String[] SOYADLAR = {
            "Yılmaz", "Demir", "Kaya", "Şahin", "Çelik", "Koç", "Arslan", "Doğan", "Aydın", "Öztürk",
            "Yıldız", "Kılıç", "Aslan", "Polat", "Erdoğan", "Güneş", "Aksoy", "Kurt", "Tekin", "Bulut"
    };

    private static final String[][] FAKULTE_BOLUM = {
            {"Gaziantep Eğitim Fakültesi", "Sınıf Öğretmenliği"},
            {"Gaziantep Eğitim Fakültesi", "Okul Öncesi Öğretmenliği"},
            {"Gaziantep Eğitim Fakültesi", "Türkçe Öğretmenliği"},
            {"Gaziantep Eğitim Fakültesi", "İlköğretim Matematik Öğretmenliği"},
            {"Teknik Bilimler Meslek Yüksekokulu", "Bilgisayar Programcılığı"},
            {"Teknik Bilimler Meslek Yüksekokulu", "Elektrik"},
            {"Kütüphane Dökümantasyon Daire Başkanlığı", "Öğrenci İşleri Destek"},
            {"Sağlık Kültür ve Spor Daire Başkanlığı", "Spor Bilimleri"}
    };

    public record DemoStudentProfile(String ad, String soyad, String fakulte, String bolum) {
    }

    private DemoAccounts() {
    }

    public static String studentNo(int index) {
        if (index < 1 || index > STUDENT_COUNT) {
            throw new IllegalArgumentException("Demo öğrenci sırası 1-" + STUDENT_COUNT + " arasında olmalıdır.");
        }
        return "9901" + String.format("%04d", index);
    }

    public static DemoStudentProfile profile(int index) {
        int slot = index - 1;
        return new DemoStudentProfile(
                ADLAR[slot % ADLAR.length],
                SOYADLAR[(slot / ADLAR.length + slot) % SOYADLAR.length],
                FAKULTE_BOLUM[slot % FAKULTE_BOLUM.length][0],
                FAKULTE_BOLUM[slot % FAKULTE_BOLUM.length][1]
        );
    }

    public static boolean isMaleName(String ad) {
        return List.of("Ahmet", "Mehmet", "Ali", "Mustafa", "Hasan", "Hüseyin", "İbrahim", "Osman",
                "Yusuf", "Ömer", "Burak", "Serkan", "Emre", "Cem", "Kaan").contains(ad);
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
