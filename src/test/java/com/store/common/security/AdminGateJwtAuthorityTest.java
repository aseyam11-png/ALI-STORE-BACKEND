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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AdminGateJwtAuthorityTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final Environment environment = mock(Environment.class);
    private final AuthSessionCookieService cookieService = mock(AuthSessionCookieService.class);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter filter(boolean gateEnabled) {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService, userDetailsService, environment, cookieService, revocationService);
        ReflectionTestUtils.setField(filter, "adminGateEnabled", gateEnabled);
        return filter;
    }

    private void runRequest(boolean gateEnabled, boolean gateClaim) throws Exception {
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("admin@example.test");
        doReturn(List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER"))).when(principal).getAuthorities();
        when(userDetailsService.loadUserByUsername("admin@example.test")).thenReturn(principal);
        when(revocationService.isRevoked("test-jwt")).thenReturn(false);
        when(jwtService.extractUsername("test-jwt")).thenReturn("admin@example.test");
        when(jwtService.isTokenValid("test-jwt", principal)).thenReturn(true);
        when(jwtService.isAdminGateVerified("test-jwt")).thenReturn(gateClaim);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer test-jwt");
        when(request.getCookies()).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter(gateEnabled).doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void stripsAdminAuthorityFromLegacyJwtWithoutGateClaim() throws Exception {
        runRequest(true, false);
        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
    }

    @Test
    void keepsAdminAuthorityOnlyWhenSignedGateClaimIsPresent() throws Exception {
        runRequest(true, true);
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void preservesExistingAdminAuthorityWhenGateIsDisabledOutsideProduction() throws Exception {
        runRequest(false, false);
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }
}
