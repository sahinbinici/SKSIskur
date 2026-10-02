package com.sks.sksiskur.web.dto;

import java.time.Instant;
import java.util.List;

public record DagitimSonucResponse(
        int kontenjan,
        int onaylanan,
        int atanan,
        int fakulteOncelikli,
        int rastgele,
        int atanamayan,
        Instant atamaTarihi,
        boolean kesinListeOnaylandi,
        boolean imzaBildirimiGonderildi,
        int yedek,
        List<BirimOzet> birimler,
        List<AtamaSatir> atanamayanlar
) {
    public record BirimOzet(
            String kod,
            String ad,
            int kontenjan,
            int dolu,
            int kalan,
            List<AtamaSatir> ogrenciler
    ) {
    }

    public record AtamaSatir(
            Long basvuruId,
            String ogrenciNo,
            String tcKimlikNo,
            String adSoyad,
            String fakulte,
            String atamaTuru,
            String birimAdi
    ) {
    }
}
