package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "kayit_listesi")
public class KayitListesi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "basvuru_donemi_id")
    private BasvuruDonemi basvuruDonemi;

    @Column(name = "kesin_onaylandi", nullable = false)
    private boolean kesinOnaylandi;

    @Column(name = "onay_tarihi")
    private Instant onayTarihi;

    @Column(name = "onaylayan_admin", length = 80)
    private String onaylayanAdmin;

    @Column(name = "kesin_liste_yukleme_tarihi")
    private Instant kesinListeYuklemeTarihi;

    @Column(name = "kesin_liste_yukleyen_admin", length = 80)
    private String kesinListeYukleyenAdmin;
}
