package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.TakipGirisPencere;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TakipGirisPencereRepository extends JpaRepository<TakipGirisPencere, Long> {

    Optional<TakipGirisPencere> findByBasvuruDonemiIdAndYilAndAy(Long basvuruDonemiId, int yil, int ay);

    java.util.List<TakipGirisPencere> findByBasvuruDonemiIdAndYilGreaterThanOrderByAyAsc(Long basvuruDonemiId, int yil);

    void deleteByBasvuruDonemiId(Long basvuruDonemiId);
}
