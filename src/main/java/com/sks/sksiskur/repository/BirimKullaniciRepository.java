package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.BirimKullanici;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BirimKullaniciRepository extends JpaRepository<BirimKullanici, Long> {
    Optional<BirimKullanici> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    List<BirimKullanici> findAllByOrderByBirimAdiAscUsernameAsc();
}
