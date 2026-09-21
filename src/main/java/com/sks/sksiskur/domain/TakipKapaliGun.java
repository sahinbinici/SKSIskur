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

import java.time.LocalDate;

@Entity
@Table(name = "takip_kapali_gunler", uniqueConstraints = {
        @UniqueConstraint(name = "uk_takip_kapali_donem_tarih", columnNames = {"basvuru_donemi_id", "tarih"})
})
public class TakipKapaliGun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_donemi_id", nullable = false)
    private BasvuruDonemi basvuruDonemi;

    @Column(nullable = false)
    private LocalDate tarih;

    public Long getId() { return id; }
    public BasvuruDonemi getBasvuruDonemi() { return basvuruDonemi; }
    public void setBasvuruDonemi(BasvuruDonemi basvuruDonemi) { this.basvuruDonemi = basvuruDonemi; }
    public LocalDate getTarih() { return tarih; }
    public void setTarih(LocalDate tarih) { this.tarih = tarih; }
}
