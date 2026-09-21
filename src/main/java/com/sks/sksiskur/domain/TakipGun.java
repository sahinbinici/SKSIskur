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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "takip_gunleri", uniqueConstraints = {
        @UniqueConstraint(name = "uk_takip_gun", columnNames = {"donem_id", "tarih"})
})
public class TakipGun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "donem_id", nullable = false)
    private TakipDonem donem;

    @Column(nullable = false)
    private LocalDate tarih;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PuantajDurum durum;

    @Column(name = "belge_adi", length = 255)
    private String belgeAdi;

    @Column(name = "belge_yolu", length = 500)
    private String belgeYolu;

    @Column(name = "icerik_tipi", length = 120)
    private String icerikTipi;
}
