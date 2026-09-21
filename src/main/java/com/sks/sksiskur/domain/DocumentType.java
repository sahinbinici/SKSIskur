package com.sks.sksiskur.domain;

public enum DocumentType {
    IKAMETGAH("İkametgah belgesi"),
    SGK_DOKUMU("SGK hizmet dökümü"),
    ADLI_SICIL("Adli sicil kaydı"),
    OGRENCI_BELGESI("Öğrenci belgesi"),
    KIMLIK_BELGESI("Kimlik belgesi"),
    // Önceki kayıtların okunabilmesi için korunur; yeni başvurularda istenmez.
    HALKBANK_IBAN("Halkbank IBAN belgesi"),
    HANE_SGK_DOKUMU("Aynı hanede yaşayanların SGK dökümü");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
