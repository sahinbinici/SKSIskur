package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.DagitimBirimi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DagitimBirimiRepository extends JpaRepository<DagitimBirimi, Long> {
    Optional<DagitimBirimi> findByBirimKodu(String birimKodu);
    boolean existsByBirimKodu(String birimKodu);
    List<DagitimBirimi> findAllByOrderByBirimAdiAsc();
}
