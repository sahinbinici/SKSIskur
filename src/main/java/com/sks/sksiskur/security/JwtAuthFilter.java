package com.sks.sksiskur.security;

import com.sks.sksiskur.domain.AdminRole;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.service.BasvuruDonemiService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final BasvuruDonemiService basvuruDonemiService;

    public JwtAuthFilter(JwtService jwtService, BasvuruDonemiService basvuruDonemiService) {
        this.jwtService = jwtService;
        this.basvuruDonemiService = basvuruDonemiService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String roleValue = claims.get("role", String.class);
                Number uid = claims.get("uid", Number.class);
                AdminRole adminRole = null;
                String adminRoleValue = claims.get("adminRole", String.class);
                if (adminRoleValue != null && !adminRoleValue.isBlank()) {
                    adminRole = AdminRole.valueOf(adminRoleValue);
                }
                AuthPrincipal principal = new AuthPrincipal(
                        uid != null ? uid.longValue() : null,
                        claims.getSubject(),
                        Role.valueOf(roleValue),
                        claims.get("birimKodu", String.class),
                        adminRole
                );
                if (principal.role() == Role.STUDENT && request.getRequestURI().startsWith("/api/student/")
                        && !basvuruDonemiService.isStudentAccessOpen()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Öğrenci başvuru giriş süresi sona erdi.\"}");
                    return;
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
