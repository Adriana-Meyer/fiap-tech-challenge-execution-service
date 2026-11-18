package com.fiap.workshop.execution.infrastructure.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(new JwtService(JwtTestTokens.SECRET));
    private final FilterChain chain = mock(FilterChain.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should authenticate the request with ROLE_ prefixed authority")
    void shouldAuthenticateTheRequestWithRolePrefixedAuthority() throws Exception {
        request.addHeader("Authorization", "Bearer " + JwtTestTokens.token("u1", "MECHANIC"));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("u1", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MECHANIC")));
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should continue unauthenticated when there is no Authorization header")
    void shouldContinueUnauthenticatedWhenThereIsNoAuthorizationHeader() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should continue unauthenticated when the token is invalid")
    void shouldContinueUnauthenticatedWhenTheTokenIsInvalid() throws Exception {
        request.addHeader("Authorization", "Bearer garbage");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should ignore non Bearer schemes")
    void shouldIgnoreNonBearerSchemes() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
