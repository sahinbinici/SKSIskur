package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.IskurBasvuruKaydi;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IskurBasvuruKaydiRepository extends JpaRepository<IskurBasvuruKaydi, Long> {

    long countByBasvuruDonemiId(Long basvuruDonemiId);

    void deleteByBasvuruDonemiId(Long basvuruDonemiId);

    void deleteByBasvuruDalgaId(Long basvuruDalgaId);

    long countByBasvuruDalgaId(Long basvuruDalgaId);

    List<IskurBasvuruKaydi> findTop50ByBasvuruDonemiIdOrderByAdAscSoyadAsc(Long basvuruDonemiId);

    List<IskurBasvuruKaydi> findByBasvuruDonemiIdOrderByAdAscSoyadAsc(Long basvuruDonemiId);

    @Query("""
            SELECT k FROM IskurBasvuruKaydi k
            WHERE k.basvuruDonemi.id = :donemId
              AND (
                    :q = ''
                    OR LOWER(k.ad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(k.soyad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CONCAT(k.ad, ' ', k.soyad)) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CONCAT(k.soyad, ' ', k.ad)) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR (k.tcKimlikNo IS NOT NULL AND k.tcKimlikNo LIKE CONCAT('%', :q, '%'))
                    OR (k.ogrenciNo IS NOT NULL AND LOWER(k.ogrenciNo) LIKE LOWER(CONCAT('%', :q, '%')))
              )
            ORDER BY k.ad ASC, k.soyad ASC
            """)
    List<IskurBasvuruKaydi> searchByDonem(
            @Param("donemId") Long donemId,
            @Param("q") String q,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(k) FROM IskurBasvuruKaydi k
            WHERE k.basvuruDonemi.id = :donemId
              AND (
                    :q = ''
                    OR LOWER(k.ad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(k.soyad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CONCAT(k.ad, ' ', k.soyad)) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CONCAT(k.soyad, ' ', k.ad)) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR (k.tcKimlikNo IS NOT NULL AND k.tcKimlikNo LIKE CONCAT('%', :q, '%'))
                    OR (k.ogrenciNo IS NOT NULL AND LOWER(k.ogrenciNo) LIKE LOWER(CONCAT('%', :q, '%')))
              )
            """)
    long countSearchByDonem(@Param("donemId") Long donemId, @Param("q") String q);

    boolean existsByBasvuruDonemiIdAndTcKimlikNo(Long basvuruDonemiId, String tcKimlikNo);

    boolean existsByBasvuruDonemiIdAndOgrenciNo(Long basvuruDonemiId, String ogrenciNo);

    boolean existsByBasvuruDonemiIdAndAdSoyadAnahtar(Long basvuruDonemiId, String adSoyadAnahtar);
}
