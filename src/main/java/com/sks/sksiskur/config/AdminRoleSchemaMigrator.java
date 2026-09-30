package com.sks.sksiskur.config;

import com.sks.sksiskur.domain.AdminRole;
import com.sks.sksiskur.domain.AdminUser;
import com.sks.sksiskur.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(6)
public class AdminRoleSchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminRoleSchemaMigrator.class);

    private final AdminUserRepository adminUserRepository;
    private final String defaultUsername;

    public AdminRoleSchemaMigrator(
            AdminUserRepository adminUserRepository,
            @Value("${app.admin.default-username}") String defaultUsername
    ) {
        this.adminUserRepository = adminUserRepository;
        this.defaultUsername = defaultUsername;
    }

    @Override
    public void run(String... args) {
        if (adminUserRepository.countByRol(AdminRole.SUPER_ADMIN) > 0) {
            return;
        }
        AdminUser promoted = adminUserRepository.findByUsername(defaultUsername)
                .orElseGet(() -> adminUserRepository.findAllByOrderByAdSoyadAscUsernameAsc().stream()
                        .findFirst()
                        .orElse(null));
        if (promoted == null) {
            return;
        }
        promoted.setRol(AdminRole.SUPER_ADMIN);
        adminUserRepository.save(promoted);
        log.info("Ana yönetici rolü atandı: {}", promoted.getUsername());
    }
}
