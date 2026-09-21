package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByOgrenciNo(String ogrenciNo);
}
