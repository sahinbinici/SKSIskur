package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.KayitListesi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KayitListesiRepository extends JpaRepository<KayitListesi, Long> {
    Optional<KayitListesi> findByBasvuruDonemiId(Long basvuruDonemiId);
}
