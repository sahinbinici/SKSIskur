package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.BirimDuyuru;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BirimDuyuruRepository extends JpaRepository<BirimDuyuru, Long> {
    List<BirimDuyuru> findAllByOrderByGonderimTarihiDesc();
}
