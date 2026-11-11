package com.fiap.workshop.execution.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService")
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(JwtTestTokens.SECRET);

    @Test
    @DisplayName("should authenticate a valid token and expose subject and role")
    void shouldAuthenticateAValidToken() {
        Optional<AuthenticatedUser> user = jwtService.authenticate(JwtTestTokens.token("12345678901", "ADMIN"));

        assertTrue(user.isPresent());
        assertEquals("12345678901", user.get().username());
        assertEquals("ADMIN", user.get().role());
    }

    @Test
    @DisplayName("should reject a token without role claim")
    void shouldRejectATokenWithoutRoleClaim() {
        assertTrue(jwtService.authenticate(JwtTestTokens.token("12345678901", null)).isEmpty());
    }

    @Test
    @DisplayName("should reject a token with a blank role claim")
    void shouldRejectATokenWithABlankRoleClaim() {
        assertTrue(jwtService.authenticate(JwtTestTokens.token("12345678901", " ")).isEmpty());
    }

    @Test
    @DisplayName("should reject a token signed with another secret")
    void shouldRejectATokenSignedWithAnotherSecret() {
        String foreign = JwtTestTokens.token("u", "ADMIN",
                "another-secret-key-that-is-also-at-least-64-characters-long-for-hmac-ok", 60_000);

        assertTrue(jwtService.authenticate(foreign).isEmpty());
    }

    @Test
    @DisplayName("should reject an expired token")
    void shouldRejectAnExpiredToken() {
        assertTrue(jwtService.authenticate(JwtTestTokens.token("u", "ADMIN", JwtTestTokens.SECRET, -1_000)).isEmpty());
    }

    @Test
    @DisplayName("should reject garbage")
    void shouldRejectGarbage() {
        assertTrue(jwtService.authenticate("not-a-jwt").isEmpty());
        assertTrue(jwtService.authenticate("").isEmpty());
    }
}
