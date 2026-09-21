package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.BasvuruDonemi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BasvuruDonemiRepository extends JpaRepository<BasvuruDonemi, Long> {

    Optional<BasvuruDonemi> findFirstByAktifTrueOrderByOlusturmaTarihiDesc();

    List<BasvuruDonemi> findAllByOrderByOlusturmaTarihiDesc();
}
