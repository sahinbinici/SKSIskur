package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.YoneticiPanosuDuyuru;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YoneticiPanosuDuyuruRepository extends JpaRepository<YoneticiPanosuDuyuru, Long> {
    List<YoneticiPanosuDuyuru> findByAktifTrueOrderByOlusturmaTarihiDesc();

    List<YoneticiPanosuDuyuru> findAllByOrderByOlusturmaTarihiDesc();
}
