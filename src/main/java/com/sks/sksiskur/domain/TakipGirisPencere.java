package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "takip_giris_pencereleri", uniqueConstraints = {
        @UniqueConstraint(name = "uk_takip_giris_donem_ay", columnNames = {"basvuru_donemi_id", "yil", "ay"})
})
public class TakipGirisPencere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_donemi_id", nullable = false)
    private BasvuruDonemi basvuruDonemi;

    @Column(nullable = false)
    private int yil;

    @Column(nullable = false)
    private int ay;

    @Column(name = "ekuant_baslangic")
    private LocalDate ekuantBaslangic;

    @Column(name = "ekuant_bitis")
    private LocalDate ekuantBitis;

    @Column(name = "puantaj_baslangic")
    private LocalDate puantajBaslangic;

    @Column(name = "puantaj_bitis")
    private LocalDate puantajBitis;
}
