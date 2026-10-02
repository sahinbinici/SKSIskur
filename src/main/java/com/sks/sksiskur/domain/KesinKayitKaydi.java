package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "kesin_kayit_kayitlari", indexes = {
        @Index(name = "idx_kesin_kayit_donem_tc", columnList = "basvuru_donemi_id, tc_kimlik_no"),
        @Index(name = "idx_kesin_kayit_donem_ogrenci", columnList = "basvuru_donemi_id, ogrenci_no"),
        @Index(name = "idx_kesin_kayit_donem_ad_soyad", columnList = "basvuru_donemi_id, ad_soyad_anahtar")
})
public class KesinKayitKaydi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_donemi_id", nullable = false)
    private BasvuruDonemi basvuruDonemi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_dalga_id")
    private BasvuruDalga basvuruDalga;

    @Column(name = "tc_kimlik_no", length = 11)
    private String tcKimlikNo;

    @Column(nullable = false, length = 80)
    private String ad;

    @Column(nullable = false, length = 80)
    private String soyad;

    @Column(name = "ogrenci_no", length = 32)
    private String ogrenciNo;

    @Column(name = "ad_soyad_anahtar", nullable = false, length = 160)
    private String adSoyadAnahtar;

    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;

    @PrePersist
    void onCreate() {
        olusturmaTarihi = Instant.now();
    }
}
