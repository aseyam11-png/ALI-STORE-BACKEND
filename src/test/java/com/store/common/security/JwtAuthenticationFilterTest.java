package com.store.common.security;

import com.store.auth.service.AuthSessionCookieService;
import com.store.auth.service.CustomUserDetailsService;
import com.store.auth.service.JwtService;
import com.store.auth.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final Environment environment = mock(Environment.class);
    private final AuthSessionCookieService cookieService = mock(AuthSessionCookieService.class);
    private final TokenRevocationService revocations = mock(TokenRevocationService.class);

    private JwtAuthenticationFilter filter(boolean gateEnabled) {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService, userDetailsService, environment, cookieService, revocations);
        ReflectionTestUtils.setField(filter, "adminGateEnabled", gateEnabled);
        return filter;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void existingAdminSession() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.invalid",
                "credentials",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void stripsAdminAuthorityFromOldSessionWithoutGateToken() throws Exception {
        existingAdminSession();
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        filter(true).doFilterInternal(request, response, chain);

        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        verify(chain).doFilter(request, response);
    }

    @Test
    void keepsAdminAuthorityWhenGateProofIsValidForTheSameUserAndTokenIsNotRevoked() throws Exception {
        existingAdminSession();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer gate-token");
        when(request.getCookies()).thenReturn(null);
        when(revocations.isRevoked("gate-token")).thenReturn(false);
        when(jwtService.isAdminGateVerified("gate-token")).thenReturn(true);
        when(jwtService.extractUsername("gate-token")).thenReturn("admin@example.invalid");

        filter(true).doFilterInternal(request, response, chain);

        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsGateProofIssuedToAnotherAdmin() throws Exception {
        existingAdminSession();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer gate-token");
        when(request.getCookies()).thenReturn(null);
        when(revocations.isRevoked("gate-token")).thenReturn(false);
        when(jwtService.isAdminGateVerified("gate-token")).thenReturn(true);
        when(jwtService.extractUsername("gate-token")).thenReturn("other-admin@example.invalid");

        filter(true).doFilterInternal(request, response, chain);

        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }
}
