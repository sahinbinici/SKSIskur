package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "basvuru_belgeleri")
public class BasvuruBelgesi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_id", nullable = false)
    private Basvuru basvuru;

    @Enumerated(EnumType.STRING)
    @Column(name = "belge_tipi", nullable = false, length = 40)
    private DocumentType belgeTipi;

    @Column(name = "orijinal_ad", nullable = false, length = 255)
    private String orijinalAd;

    @Column(name = "saklama_yolu", nullable = false, length = 500)
    private String saklamaYolu;

    @Column(name = "icerik_tipi", length = 120)
    private String icerikTipi;

    @Column(name = "boyut_byte")
    private Long boyutByte;

    @Enumerated(EnumType.STRING)
    @Column(name = "dogrulama_durumu", length = 30)
    private BelgeDogrulamaDurumu dogrulamaDurumu;

    @Column(name = "dogrulama_notu", length = 500)
    private String dogrulamaNotu;

    @Column(name = "hane_uyesi_adi", length = 120)
    private String haneUyesiAdi;

    @Column(name = "yukleme_tarihi", nullable = false)
    private Instant yuklemeTarihi;

    @PrePersist
    void onCreate() {
        yuklemeTarihi = Instant.now();
    }
}
