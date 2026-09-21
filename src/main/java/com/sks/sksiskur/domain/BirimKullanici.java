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
@Table(name = "birim_kullanicilar", indexes = {
        @Index(name = "uk_birim_kullanici_username", columnList = "username", unique = true),
        @Index(name = "idx_birim_kullanici_kod", columnList = "birim_kodu")
})
public class BirimKullanici {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String adSoyad;

    @Column(name = "birim_kodu", nullable = false, length = 32)
    private String birimKodu;

    @Column(name = "birim_adi", nullable = false, length = 200)
    private String birimAdi;

    @Column(nullable = false)
    private boolean aktif = true;

    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;

    @Column(name = "guncelleme_tarihi", nullable = false)
    private Instant guncellemeTarihi;

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
