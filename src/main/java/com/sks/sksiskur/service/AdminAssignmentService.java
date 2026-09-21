package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.AdminUser;
import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.AdminUserRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Başvuruları aktif yöneticilerin bekleyen iş yüküne göre dengeler. */
@Service
public class AdminAssignmentService {

    private final AdminUserRepository adminUserRepository;
    private final BasvuruRepository basvuruRepository;

    public AdminAssignmentService(AdminUserRepository adminUserRepository, BasvuruRepository basvuruRepository) {
        this.adminUserRepository = adminUserRepository;
        this.basvuruRepository = basvuruRepository;
    }

    @Transactional
    public void assign(Basvuru basvuru) {
        if (basvuru.getAtananAdmin() != null) {
            return;
        }
        List<AdminUser> admins = activeAdmins();
        Map<Long, Long> loads = currentLoads(admins);
        basvuru.setAtananAdmin(leastLoaded(admins, loads));
    }

    @Transactional
    public int assignUnassigned(BasvuruDonemi donem) {
        List<AdminUser> admins = activeAdmins();
        Map<Long, Long> loads = currentLoads(admins);
        List<Basvuru> waiting = basvuruRepository
                .findByStatusAndAtananAdminIsNullAndBasvuruDonemiIdOrderByGonderimTarihiAsc(ApplicationStatus.SUBMITTED, donem.getId());
        for (Basvuru basvuru : waiting) {
            AdminUser selected = leastLoaded(admins, loads);
            basvuru.setAtananAdmin(selected);
            loads.compute(selected.getId(), (id, count) -> count == null ? 1 : count + 1);
        }
        return waiting.size();
    }

    @Transactional
    public void reassignPendingFrom(AdminUser inactiveAdmin) {
        List<AdminUser> admins = activeAdmins();
        Map<Long, Long> loads = currentLoads(admins);
        for (Basvuru basvuru : basvuruRepository.findByStatusAndAtananAdminId(ApplicationStatus.SUBMITTED, inactiveAdmin.getId())) {
            AdminUser selected = leastLoaded(admins, loads);
            basvuru.setAtananAdmin(selected);
            loads.compute(selected.getId(), (id, count) -> count == null ? 1 : count + 1);
        }
    }

    private List<AdminUser> activeAdmins() {
        List<AdminUser> admins = adminUserRepository.findByAktifTrueOrderByIdAsc();
        if (admins.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Başvuru atamak için en az bir aktif yönetici gerekir.");
        }
        return admins;
    }

    private Map<Long, Long> currentLoads(List<AdminUser> admins) {
        Map<Long, Long> loads = new HashMap<>();
        for (AdminUser admin : admins) {
            loads.put(admin.getId(), basvuruRepository.countByAtananAdminIdAndStatus(admin.getId(), ApplicationStatus.SUBMITTED));
        }
        return loads;
    }

    private AdminUser leastLoaded(List<AdminUser> admins, Map<Long, Long> loads) {
        return admins.stream()
                .min(Comparator.comparing((AdminUser admin) -> loads.getOrDefault(admin.getId(), 0L))
                        .thenComparing(AdminUser::getId))
                .orElseThrow();
    }
}
