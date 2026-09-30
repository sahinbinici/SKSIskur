package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.IslemLog;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IslemLogRepository extends JpaRepository<IslemLog, Long> {

    @Query("""
            SELECT l FROM IslemLog l
            WHERE (:tur IS NULL OR l.tur = :tur)
              AND (:rol IS NULL OR l.rol = :rol)
              AND (:kullanici IS NULL OR LOWER(l.kullaniciAdi) LIKE LOWER(CONCAT('%', :kullanici, '%'))
                   OR LOWER(COALESCE(l.adSoyad, '')) LIKE LOWER(CONCAT('%', :kullanici, '%')))
              AND (:from IS NULL OR l.zaman >= :from)
              AND (:to IS NULL OR l.zaman <= :to)
            ORDER BY l.zaman DESC
            """)
    Page<IslemLog> search(
            @Param("tur") IslemTuru tur,
            @Param("rol") Role rol,
            @Param("kullanici") String kullanici,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
