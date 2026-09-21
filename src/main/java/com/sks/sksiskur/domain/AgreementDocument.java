package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sozlesme_metinleri")
public class AgreementDocument {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tur", nullable = false, length = 32)
    private AgreementType type;

    @Column(nullable = false, length = 180)
    private String baslik;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String icerik;

    @Column(nullable = false)
    private int versiyon;

    @Column(name = "guncelleme_tarihi", nullable = false)
    private Instant guncellemeTarihi;

    @PrePersist
    @PreUpdate
    void touch() {
        guncellemeTarihi = Instant.now();
    }
}
