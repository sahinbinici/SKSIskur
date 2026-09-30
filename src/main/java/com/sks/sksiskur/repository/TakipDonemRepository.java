package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.TakipDonem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TakipDonemRepository extends JpaRepository<TakipDonem, Long> {

    @EntityGraph(attributePaths = {"gunler", "basvuru", "basvuru.student"})
    Optional<TakipDonem> findByBasvuruIdAndYilAndAy(Long basvuruId, int yil, int ay);

    @EntityGraph(attributePaths = {"gunler"})
    List<TakipDonem> findByBasvuruId(Long basvuruId);

    @EntityGraph(attributePaths = {"gunler", "basvuru", "basvuru.student"})
    List<TakipDonem> findByYilAndAyAndBasvuruIdIn(int yil, int ay, Collection<Long> basvuruIds);
}
