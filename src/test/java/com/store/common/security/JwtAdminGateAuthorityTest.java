package com.store.common.security;

import com.store.auth.service.AccountUserDetails;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JwtAdminGateAuthorityTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final Environment environment = mock(Environment.class);
    private final AuthSessionCookieService cookieService = mock(AuthSessionCookieService.class);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void legacyAdminTokenCannotRetainAdminAuthorityWhenGateIsEnabled() throws Exception {
        var authorities = List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails admin = new AccountUserDetails(
                "admin@example.com", "encoded", true, true, authorities, 0L);
        var authentication = runFilter(admin, false);

        assertFalse(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
    }

    @Test
    void sessionAuthenticationWithoutJwtCannotRetainAdminAuthority() throws Exception {
        var authorities = List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails admin = new AccountUserDetails(
                "admin@example.com", "encoded", true, true, authorities, 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, authorities));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService, userDetailsService, environment, cookieService, revocationService);
        ReflectionTestUtils.setField(filter, "adminGateEnabled", true);
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(admin);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        var updated = SecurityContextHolder.getContext().getAuthentication();
        assertFalse(updated.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        assertTrue(updated.getAuthorities().stream()
                .anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        verify(chain).doFilter(request, response);
    }

    @Test
    void adminTokenWithSignedGateProofRetainsAdminAuthority() throws Exception {
        var authorities = List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails admin = new AccountUserDetails(
                "admin@example.com", "encoded", true, true, authorities, 0L);
        var authentication = runFilter(admin, true);

        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    private org.springframework.security.core.Authentication runFilter(
            AccountUserDetails admin, boolean gateProof) throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService, userDetailsService, environment, cookieService, revocationService);
        ReflectionTestUtils.setField(filter, "adminGateEnabled", true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer test-admin-token");
        when(request.getCookies()).thenReturn(null);
        when(revocationService.isRevoked("test-admin-token")).thenReturn(false);
        when(jwtService.extractUsername("test-admin-token")).thenReturn("admin@example.com");
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(admin);
        when(jwtService.isTokenValid("test-admin-token", admin)).thenReturn(true);
        when(jwtService.isAdminGateVerified("test-admin-token")).thenReturn(gateProof);

        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
