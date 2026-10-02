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
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "basvuru_dalgalar")
public class BasvuruDalga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_donemi_id", nullable = false)
    private BasvuruDonemi basvuruDonemi;

    @Column(name = "tur_no", nullable = false)
    private int turNo;

    @Column(nullable = false, length = 120)
    private String ad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BasvuruDalgaDurum durum = BasvuruDalgaDurum.ACIK;

    @Column(name = "aktif", nullable = false)
    private boolean aktif;

    @Column(name = "ogrenci_baslangic_tarihi")
    private LocalDate ogrenciBaslangicTarihi;

    @Column(name = "ogrenci_bitis_tarihi")
    private LocalDate ogrenciBitisTarihi;

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

    @Column(name = "imza_bildirimi_gonderildi", nullable = false)
    private boolean imzaBildirimiGonderildi;

    @Column(name = "imza_bildirimi_gonderim_tarihi")
    private Instant imzaBildirimiGonderimTarihi;

    @Column(name = "imza_bildirimi_gonderen_admin", length = 80)
    private String imzaBildirimiGonderenAdmin;

    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;

    @PrePersist
    void onCreate() {
        if (olusturmaTarihi == null) {
            olusturmaTarihi = Instant.now();
        }
    }
}
