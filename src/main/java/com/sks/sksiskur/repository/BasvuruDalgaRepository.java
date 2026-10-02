package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.BasvuruDalga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BasvuruDalgaRepository extends JpaRepository<BasvuruDalga, Long> {

    List<BasvuruDalga> findByBasvuruDonemiIdOrderByTurNoAsc(Long basvuruDonemiId);

    Optional<BasvuruDalga> findByBasvuruDonemiIdAndAktifTrue(Long basvuruDonemiId);

    Optional<BasvuruDalga> findTopByBasvuruDonemiIdOrderByTurNoDesc(Long basvuruDonemiId);

    int countByBasvuruDonemiId(Long basvuruDonemiId);
}
