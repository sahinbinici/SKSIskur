package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.TakipGun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface TakipGunRepository extends JpaRepository<TakipGun, Long> {
    Optional<TakipGun> findByDonemIdAndTarih(Long donemId, LocalDate tarih);
}
