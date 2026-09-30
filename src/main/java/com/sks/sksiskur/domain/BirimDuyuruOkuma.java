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

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "birim_duyuru_okumalar", uniqueConstraints = {
        @UniqueConstraint(name = "uk_birim_duyuru_okuma", columnNames = {"duyuru_id", "birim_kodu"})
})
public class BirimDuyuruOkuma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "duyuru_id", nullable = false)
    private BirimDuyuru duyuru;

    @Column(name = "birim_kodu", nullable = false, length = 80)
    private String birimKodu;

    @Column(nullable = false)
    private boolean okundu;

    @Column(name = "okuma_tarihi")
    private Instant okumaTarihi;
}
