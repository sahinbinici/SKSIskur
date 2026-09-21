package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    List<AdminUser> findAllByOrderByAdSoyadAscUsernameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AdminUser> findByAktifTrueOrderByIdAsc();
}
