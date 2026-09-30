package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "islem_loglari", indexes = {
        @Index(name = "idx_islem_log_zaman", columnList = "zaman"),
        @Index(name = "idx_islem_log_kullanici", columnList = "kullanici_adi"),
        @Index(name = "idx_islem_log_tur", columnList = "tur")
})
public class IslemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant zaman;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role rol;

    @Column(name = "kullanici_adi", nullable = false, length = 80)
    private String kullaniciAdi;

    @Column(name = "ad_soyad", length = 120)
    private String adSoyad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IslemTuru tur;

    @Column(name = "varlik_tipi", length = 40)
    private String varlikTipi;

    @Column(name = "varlik_id")
    private Long varlikId;

    @Column(nullable = false, length = 500)
    private String aciklama;

    @Column(length = 2000)
    private String detay;

    @PrePersist
    void onCreate() {
        if (zaman == null) {
            zaman = Instant.now();
        }
    }
}
