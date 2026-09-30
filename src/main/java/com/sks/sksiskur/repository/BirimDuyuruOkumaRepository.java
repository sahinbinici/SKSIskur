package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.BirimDuyuruOkuma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BirimDuyuruOkumaRepository extends JpaRepository<BirimDuyuruOkuma, Long> {
    List<BirimDuyuruOkuma> findByBirimKoduOrderByDuyuru_GonderimTarihiDesc(String birimKodu);

    Optional<BirimDuyuruOkuma> findByDuyuruIdAndBirimKodu(Long duyuruId, String birimKodu);

    long countByDuyuruId(Long duyuruId);

    long countByDuyuruIdAndOkunduTrue(Long duyuruId);

    List<BirimDuyuruOkuma> findByDuyuruIdOrderByBirimKoduAsc(Long duyuruId);
}
