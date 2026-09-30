package com.sks.sksiskur.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subject, String role, Long userId) {
        return generateToken(subject, role, userId, null);
    }

    public String generateToken(String subject, String role, Long userId, String birimKodu) {
        return generateToken(subject, role, userId, birimKodu, null);
    }

    public String generateToken(String subject, String role, Long userId, String birimKodu, String adminRole) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)));
        if (birimKodu != null && !birimKodu.isBlank()) {
            builder.claim("birimKodu", birimKodu);
        }
        if (adminRole != null && !adminRole.isBlank()) {
            builder.claim("adminRole", adminRole);
        }
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
