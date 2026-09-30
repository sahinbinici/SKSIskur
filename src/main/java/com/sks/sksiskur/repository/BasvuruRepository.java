package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.domain.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BasvuruRepository extends JpaRepository<Basvuru, Long> {
    @EntityGraph(attributePaths = {"student", "belgeler"})
    Optional<Basvuru> findByStudentAndBasvuruDonemiId(Student student, Long basvuruDonemiId);

    @EntityGraph(attributePaths = {"student", "belgeler"})
    Optional<Basvuru> findByStudentOgrenciNoAndBasvuruDonemiAktifTrue(String ogrenciNo);

    @EntityGraph(attributePaths = {"student", "belgeler", "atananAdmin"})
    @Query("SELECT b FROM Basvuru b WHERE b.id = :id")
    Optional<Basvuru> findDetailedById(@Param("id") Long id);

    List<Basvuru> findAllByOrderByGuncellemeTarihiDesc();

    List<Basvuru> findByStatusAndBasvuruDonemiIdOrderByGuncellemeTarihiDesc(ApplicationStatus status, Long basvuruDonemiId);

    long countByStatusAndBasvuruDonemiId(ApplicationStatus status, Long basvuruDonemiId);

    long countByStatusAndAtananBirimKoduIsNotNullAndBasvuruDonemiId(ApplicationStatus status, Long basvuruDonemiId);

    long countByStatusAndKayitTuruAndBasvuruDonemiId(ApplicationStatus status, KayitTuru kayitTuru, Long basvuruDonemiId);

    long countByStatusAndKayitTuruIsNullAndBasvuruDonemiId(ApplicationStatus status, Long basvuruDonemiId);

    long countByBasvuruDonemiId(Long basvuruDonemiId);

    long countByAtananAdminIdAndStatus(Long adminId, ApplicationStatus status);

    @EntityGraph(attributePaths = {"student", "belgeler", "atananAdmin"})
    List<Basvuru> findByStatusAndAtananAdminIsNullAndBasvuruDonemiIdOrderByGonderimTarihiAsc(
            ApplicationStatus status, Long basvuruDonemiId
    );

    @EntityGraph(attributePaths = {"atananAdmin"})
    List<Basvuru> findByStatusAndAtananAdminId(ApplicationStatus status, Long adminId);

    @EntityGraph(attributePaths = {"student", "belgeler", "atananAdmin"})
    List<Basvuru> findByStatusAndBasvuruDonemiId(ApplicationStatus status, Long basvuruDonemiId);

    @EntityGraph(attributePaths = {"student"})
    List<Basvuru> findByStatusAndKayitTuruAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
            ApplicationStatus status,
            KayitTuru kayitTuru,
            Long basvuruDonemiId
    );

    @EntityGraph(attributePaths = {"student"})
    List<Basvuru> findByStatusAndKayitTuruAndKesinListedeTrueAndAtananBirimKoduIsNullAndBasvuruDonemiId(
            ApplicationStatus status,
            KayitTuru kayitTuru,
            Long basvuruDonemiId
    );

    @EntityGraph(attributePaths = {"student"})
    List<Basvuru> findByKayitTuruAndAtananBirimKoduIsNotNullAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(KayitTuru kayitTuru, Long basvuruDonemiId);

    @EntityGraph(attributePaths = {"student"})
    List<Basvuru> findByAtananBirimKoduIsNotNullAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(Long basvuruDonemiId);

    @EntityGraph(attributePaths = {"student"})
    List<Basvuru> findByAtananBirimKoduAndStatusAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
            String atananBirimKodu,
            ApplicationStatus status,
            Long basvuruDonemiId
    );

    @EntityGraph(attributePaths = {"student"})
    List<Basvuru> findByAtananBirimKoduAndKayitTuruAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
            String atananBirimKodu,
            KayitTuru kayitTuru,
            Long basvuruDonemiId
    );

    @EntityGraph(attributePaths = {"student"})
    List<Basvuru> findByBasvuruDonemiIdAndIliskiBitisTarihiIsNotNullOrderByIliskiBitisTarihiAsc(
            Long basvuruDonemiId
    );

    @EntityGraph(attributePaths = {"student", "belgeler"})
    Optional<Basvuru> findDetailedByIdAndAtananBirimKoduAndBasvuruDonemiAktifTrue(Long id, String atananBirimKodu);

    @Query("""
            SELECT b FROM Basvuru b
            JOIN FETCH b.student s
            WHERE b.basvuruDonemi.id = :donemId
              AND (:status IS NULL OR b.status = :status)
              AND (
                    :q IS NULL OR :q = ''
                    OR LOWER(s.ogrenciNo) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(s.ad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(s.soyad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CONCAT(s.ad, ' ', s.soyad)) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            ORDER BY b.guncellemeTarihi DESC
            """)
    List<Basvuru> search(@Param("donemId") Long donemId, @Param("status") ApplicationStatus status, @Param("q") String q);

    @EntityGraph(attributePaths = {"student", "belgeler", "atananAdmin"})
    @Query("""
            SELECT b FROM Basvuru b
            JOIN FETCH b.student s
            WHERE b.basvuruDonemi.id = :donemId
              AND b.atananAdmin.username = :username
              AND (:status IS NULL OR b.status = :status)
              AND (
                    :q IS NULL OR :q = ''
                    OR LOWER(s.ogrenciNo) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(s.ad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(s.soyad) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CONCAT(s.ad, ' ', s.soyad)) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            ORDER BY b.guncellemeTarihi DESC
            """)
    List<Basvuru> searchAssignedTo(@Param("donemId") Long donemId, @Param("status") ApplicationStatus status,
                                   @Param("q") String q, @Param("username") String username);
}
