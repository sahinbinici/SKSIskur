package com.sks.sksiskur.security;

import com.sks.sksiskur.domain.AdminRole;
import com.sks.sksiskur.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AuthPrincipal(Long userId, String username, Role role, String birimKodu, AdminRole adminRole) implements UserDetails {

    public AuthPrincipal(Long userId, String username, Role role, String birimKodu) {
        this(userId, username, role, birimKodu, null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == Role.ADMIN && adminRole == AdminRole.SUPER_ADMIN) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_" + role.name()),
                    new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")
            );
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean superAdmin() {
        return role == Role.ADMIN && adminRole == AdminRole.SUPER_ADMIN;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
