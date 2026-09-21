package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ogrenciler", indexes = {
        @Index(name = "uk_ogrenci_no", columnList = "ogrenci_no", unique = true)
})
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ogrenci_no", nullable = false, unique = true, length = 32)
    private String ogrenciNo;

    @Column(name = "tc_kimlik_no", length = 11)
    private String tcKimlikNo;

    @Column(nullable = false, length = 80)
    private String ad;

    @Column(nullable = false, length = 80)
    private String soyad;

    @Column(length = 40)
    private String uyruk;

    @Column(name = "dogum_yeri", length = 80)
    private String dogumYeri;

    @Column(name = "dogum_tarihi", length = 20)
    private String dogumTarihi;

    @Column(length = 20)
    private String cinsiyet;

    @Column(name = "egitim_derecesi", length = 40)
    private String egitimDerecesi;

    @Column(name = "kayit_tarihi", length = 20)
    private String kayitTarihi;

    @Column(name = "ogrenim_durumu", length = 120)
    private String ogrenimDurumu;

    @Column(name = "fakulte", length = 160)
    private String fakulte;

    @Column(name = "bolum", length = 160)
    private String bolum;

    @Column(name = "program", length = 160)
    private String program;

    @Column(length = 10)
    private String sinif;

    @Column(length = 40)
    private String durumu;

    @Column(length = 120)
    private String eposta;

    @Column(length = 20)
    private String gsm;

    @Column(length = 255)
    private String adres;

    @Column(length = 80)
    private String il;

    @Column(length = 80)
    private String ilce;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(name = "danisman", length = 160)
    private String danisman;

    @Column(name = "demo_sifre_hash", length = 120)
    private String demoSifreHash;

    @Column(name = "kvkk_sozlesme_versiyonu")
    private Integer kvkkSozlesmeVersiyonu;

    @Column(name = "kvkk_onay_tarihi")
    private Instant kvkkOnayTarihi;

    @Column(name = "iskur_sozlesme_versiyonu")
    private Integer iskurSozlesmeVersiyonu;

    @Column(name = "iskur_sozlesme_onay_tarihi")
    private Instant iskurSozlesmeOnayTarihi;

    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;

    @Column(name = "guncelleme_tarihi", nullable = false)
    private Instant guncellemeTarihi;

    public String getAdSoyad() {
        return (ad + " " + soyad).trim();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        olusturmaTarihi = now;
        guncellemeTarihi = now;
    }

    @PreUpdate
    void onUpdate() {
        guncellemeTarihi = Instant.now();
    }
}
