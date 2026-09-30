package com.sks.sksiskur.config;

import com.sks.sksiskur.domain.AdminRole;
import com.sks.sksiskur.domain.AdminUser;
import com.sks.sksiskur.repository.AdminUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String name;

    public DataInitializer(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.default-username}") String username,
            @Value("${app.admin.default-password}") String password,
            @Value("${app.admin.default-name}") String name
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(String... args) {
        adminUserRepository.findByUsername(username).ifPresentOrElse(admin -> {
            boolean changed = false;
            if (name != null && !name.equals(admin.getAdSoyad())) {
                admin.setAdSoyad(name);
                changed = true;
            }
            if (admin.getRol() != AdminRole.SUPER_ADMIN) {
                admin.setRol(AdminRole.SUPER_ADMIN);
                changed = true;
            }
            if (changed) {
                adminUserRepository.save(admin);
            }
        }, () -> {
            AdminUser admin = new AdminUser();
            admin.setUsername(username);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setAdSoyad(name);
            admin.setAktif(true);
            admin.setRol(AdminRole.SUPER_ADMIN);
            adminUserRepository.save(admin);
        });
    }
}
