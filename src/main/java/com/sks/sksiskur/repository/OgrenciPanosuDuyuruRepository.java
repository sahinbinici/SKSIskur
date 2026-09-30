package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.OgrenciPanosuDuyuru;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OgrenciPanosuDuyuruRepository extends JpaRepository<OgrenciPanosuDuyuru, Long> {
    List<OgrenciPanosuDuyuru> findByAktifTrueOrderByOlusturmaTarihiDesc();

    List<OgrenciPanosuDuyuru> findAllByOrderByOlusturmaTarihiDesc();
}
