package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IskurBasvuruKaydiRepository extends JpaRepository<IskurBasvuruKaydi, Long> {

    long countByBasvuruDonemiId(Long basvuruDonemiId);

    void deleteByBasvuruDonemiId(Long basvuruDonemiId);

    List<IskurBasvuruKaydi> findTop50ByBasvuruDonemiIdOrderByAdAscSoyadAsc(Long basvuruDonemiId);

    List<IskurBasvuruKaydi> findByBasvuruDonemiIdOrderByAdAscSoyadAsc(Long basvuruDonemiId);

    boolean existsByBasvuruDonemiIdAndTcKimlikNo(Long basvuruDonemiId, String tcKimlikNo);

    boolean existsByBasvuruDonemiIdAndOgrenciNo(Long basvuruDonemiId, String ogrenciNo);

    boolean existsByBasvuruDonemiIdAndAdSoyadAnahtar(Long basvuruDonemiId, String adSoyadAnahtar);
}
