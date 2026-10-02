package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.KesinKayitKaydi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KesinKayitKaydiRepository extends JpaRepository<KesinKayitKaydi, Long> {

    long countByBasvuruDonemiId(Long basvuruDonemiId);

    void deleteByBasvuruDonemiId(Long basvuruDonemiId);

    void deleteByBasvuruDalgaId(Long basvuruDalgaId);

    List<KesinKayitKaydi> findByBasvuruDonemiIdOrderByAdAscSoyadAsc(Long basvuruDonemiId);

    List<KesinKayitKaydi> findByBasvuruDalgaIdOrderByAdAscSoyadAsc(Long basvuruDalgaId);

    long countByBasvuruDalgaId(Long basvuruDalgaId);
}
