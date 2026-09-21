package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "basvuru_donemleri")
public class BasvuruDonemi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String ad;

    @Column(nullable = false)
    private boolean aktif;

    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;

    @Column(name = "kapanis_tarihi")
    private Instant kapanisTarihi;

    @Column(name = "ogrenci_baslangic_tarihi")
    private LocalDate ogrenciBaslangicTarihi;

    @Column(name = "ogrenci_bitis_tarihi")
    private LocalDate ogrenciBitisTarihi;

    @Column(name = "aylik_gelir_limiti", nullable = false, precision = 12, scale = 2)
    private BigDecimal aylikGelirLimiti;

    @PrePersist
    void onCreate() {
        olusturmaTarihi = Instant.now();
    }
}
