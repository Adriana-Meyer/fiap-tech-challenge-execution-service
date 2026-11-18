package com.fiap.workshop.execution.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** Builds tokens the way the OS service does, for tests. */
public final class JwtTestTokens {

    public static final String SECRET = "test-secret-key-for-unit-tests-must-be-at-least-64-characters-long-ok";

    private JwtTestTokens() {}

    public static String token(String subject, String role) {
        return token(subject, role, SECRET, 60_000);
    }

    public static String token(String subject, String role, String secret, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key).compact();
    }
}
