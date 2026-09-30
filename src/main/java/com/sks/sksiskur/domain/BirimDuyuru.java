package com.sks.sksiskur.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "birim_duyurulari")
public class BirimDuyuru {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String baslik;

    @Column(nullable = false, length = 4000)
    private String mesaj;

    @Column(name = "gonderen_admin", nullable = false, length = 120)
    private String gonderenAdmin;

    @Column(name = "gonderim_tarihi", nullable = false)
    private Instant gonderimTarihi;

    @Column(name = "tum_birimler", nullable = false)
    private boolean tumBirimler;

    @PrePersist
    void onCreate() {
        if (gonderimTarihi == null) {
            gonderimTarihi = Instant.now();
        }
    }
}
