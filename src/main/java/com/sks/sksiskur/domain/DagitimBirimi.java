package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "dagitim_birimleri")
public class DagitimBirimi {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "birim_kodu", nullable = false, unique = true, length = 40)
    private String birimKodu;
    @Column(name = "birim_adi", nullable = false, length = 200)
    private String birimAdi;
    @Column(name = "kamu_kodu")
    private Integer kamuKodu;
    @Column(nullable = false, length = 20)
    private String kaynak;
    @Column(nullable = false)
    private int kontenjan;
    @Column(name = "dagitima_acik", nullable = false)
    private boolean dagitimaAcik;
    @Column(name = "olusturma_tarihi", nullable = false)
    private Instant olusturmaTarihi;
    @Column(name = "guncelleme_tarihi", nullable = false)
    private Instant guncellemeTarihi;

    @PrePersist void createDates() { olusturmaTarihi = Instant.now(); guncellemeTarihi = olusturmaTarihi; }
    @PreUpdate void updateDate() { guncellemeTarihi = Instant.now(); }
}
