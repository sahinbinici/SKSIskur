package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BasvuruBelgesiRepository extends JpaRepository<BasvuruBelgesi, Long> {
    Optional<BasvuruBelgesi> findByBasvuruAndBelgeTipi(Basvuru basvuru, DocumentType belgeTipi);

    Optional<BasvuruBelgesi> findByIdAndBasvuruId(Long id, Long basvuruId);
}
