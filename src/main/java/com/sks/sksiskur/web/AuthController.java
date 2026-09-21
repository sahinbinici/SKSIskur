package com.sks.sksiskur.web;

import com.sks.sksiskur.security.AuthPrincipal;
import com.sks.sksiskur.web.dto.AdminLoginRequest;
import com.sks.sksiskur.web.dto.AuthResponse;
import com.sks.sksiskur.web.dto.DemoInfoResponse;
import com.sks.sksiskur.web.dto.StudentLoginRequest;
import com.sks.sksiskur.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/ogrenci")
    public AuthResponse studentLogin(@Valid @RequestBody StudentLoginRequest request, HttpServletRequest httpRequest) {
        return authService.studentLogin(request, httpRequest);
    }

    @PostMapping("/admin")
    public AuthResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return authService.adminLogin(request);
    }

    @PostMapping("/birim")
    public AuthResponse unitLogin(@Valid @RequestBody AdminLoginRequest request) {
        return authService.unitLogin(request);
    }

    @GetMapping("/demo")
    public DemoInfoResponse demoInfo() {
        return authService.demoInfo();
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return Map.of(
                "username", principal.getUsername(),
                "role", principal.role().name(),
                "userId", principal.userId() == null ? 0 : principal.userId(),
                "birimKodu", principal.birimKodu() == null ? "" : principal.birimKodu()
        );
    }
}
