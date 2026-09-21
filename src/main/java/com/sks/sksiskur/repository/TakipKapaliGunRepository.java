package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.TakipKapaliGun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TakipKapaliGunRepository extends JpaRepository<TakipKapaliGun, Long> {

    List<TakipKapaliGun> findByBasvuruDonemiIdAndTarihBetweenOrderByTarihAsc(Long basvuruDonemiId, LocalDate start, LocalDate end);

    long deleteByBasvuruDonemiIdAndTarihBetween(Long basvuruDonemiId, LocalDate start, LocalDate end);
}
