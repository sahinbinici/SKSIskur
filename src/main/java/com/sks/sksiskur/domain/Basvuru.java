package com.sks.sksiskur.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "basvurular", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_basvuru_ogrenci_donem", columnNames = {"ogrenci_id", "basvuru_donemi_id"})
})
public class Basvuru {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_donemi_id")
    private BasvuruDonemi basvuruDonemi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    @Column(length = 32)
    private String iban;

    @Column(name = "hesap_sahibi", length = 120)
    private String hesapSahibi;

    @Column(name = "aylik_gelir", precision = 12, scale = 2)
    private BigDecimal aylikGelir;

    @Column(name = "admin_notu", length = 2000)
    private String adminNotu;

    @Column(name = "inceleyen_admin", length = 80)
    private String inceleyenAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atanan_admin_id")
    private AdminUser atananAdmin;

    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;

    @Column(name = "guncelleme_tarihi", nullable = false)
    private Instant guncellemeTarihi;

    @Column(name = "gonderim_tarihi")
    private Instant gonderimTarihi;

    @Column(name = "inceleme_tarihi")
    private Instant incelemeTarihi;

    @Column(name = "atanan_birim_kodu", length = 40)
    private String atananBirimKodu;

    @Column(name = "atanan_birim_adi", length = 160)
    private String atananBirimAdi;

    @Column(name = "atama_turu", length = 20)
    private String atamaTuru;

    @Column(name = "atama_tarihi")
    private Instant atamaTarihi;

    @Column(name = "iliski_bitis_tarihi")
    private LocalDate iliskiBitisTarihi;

    @Enumerated(EnumType.STRING)
    @Column(name = "kayit_turu", length = 20)
    private KayitTuru kayitTuru;

    @Column(name = "kesin_listede")
    private Boolean kesinListede;

    @Column(name = "kayit_tarihi")
    private Instant kayitTarihi;

    @Column(name = "atama_bildirimi_okundu", nullable = false)
    private boolean atamaBildirimiOkundu = false;

    @OneToMany(mappedBy = "basvuru", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BasvuruBelgesi> belgeler = new ArrayList<>();

    public boolean isAssigned() {
        return atananBirimKodu != null && !atananBirimKodu.isBlank();
    }

    public boolean canWorkIn(YearMonth month) {
        return iliskiBitisTarihi == null || YearMonth.from(iliskiBitisTarihi).isAfter(month);
    }

    public void clearAssignment() {
        atananBirimKodu = null;
        atananBirimAdi = null;
        atamaTuru = null;
        atamaTarihi = null;
    }

    public boolean isLockedForStudent() {
        return status == ApplicationStatus.SUBMITTED || status == ApplicationStatus.APPROVED;
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
