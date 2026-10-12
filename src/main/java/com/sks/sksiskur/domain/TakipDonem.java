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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "takip_donemleri", uniqueConstraints = {
        @UniqueConstraint(name = "uk_takip_basvuru_ay", columnNames = {"basvuru_id", "yil", "ay"})
})
public class TakipDonem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "basvuru_id", nullable = false)
    private Basvuru basvuru;

    @Column(nullable = false)
    private int yil;

    @Column(nullable = false)
    private int ay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TakipStatus status = TakipStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "ekuant_status", length = 20)
    private TakipStatus ekuantStatus = TakipStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "puantaj_status", length = 20)
    private TakipStatus puantajStatus = TakipStatus.DRAFT;

    @Column(name = "gonderim_tarihi")
    private Instant gonderimTarihi;

    @Column(name = "ekuant_gonderim_tarihi")
    private Instant ekuantGonderimTarihi;

    @Column(name = "puantaj_gonderim_tarihi")
    private Instant puantajGonderimTarihi;

    @Column(name = "onay_tarihi")
    private Instant onayTarihi;

    @Column(name = "ekuant_onay_tarihi")
    private Instant ekuantOnayTarihi;

    @Column(name = "puantaj_onay_tarihi")
    private Instant puantajOnayTarihi;

    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;

    @Column(name = "guncelleme_tarihi", nullable = false)
    private Instant guncellemeTarihi;

    @OneToMany(mappedBy = "donem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TakipGun> gunler = new ArrayList<>();

    public TakipStatus resolveEkuantStatus() {
        return ekuantStatus != null ? ekuantStatus : status;
    }

    public TakipStatus resolvePuantajStatus() {
        return puantajStatus != null ? puantajStatus : status;
    }

    public boolean isEkuantLocked() {
        TakipStatus value = resolveEkuantStatus();
        return value == TakipStatus.SUBMITTED || value == TakipStatus.APPROVED;
    }

    public boolean isPuantajLocked() {
        TakipStatus value = resolvePuantajStatus();
        return value == TakipStatus.SUBMITTED || value == TakipStatus.APPROVED;
    }

    public boolean isLocked() {
        return isEkuantLocked() && isPuantajLocked();
    }

    public void syncOverallStatus() {
        if (resolvePuantajStatus() == TakipStatus.APPROVED) {
            status = TakipStatus.APPROVED;
        } else if (resolveEkuantStatus() == TakipStatus.SUBMITTED
                || resolvePuantajStatus() == TakipStatus.SUBMITTED) {
            status = TakipStatus.SUBMITTED;
        } else {
            status = TakipStatus.DRAFT;
        }
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
