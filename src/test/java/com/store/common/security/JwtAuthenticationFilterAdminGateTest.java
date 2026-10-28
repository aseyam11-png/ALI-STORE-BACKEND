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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterAdminGateTest {

    private static final String EMAIL = "admin@example.invalid";
    private static final String TOKEN = "signed-token";

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final Environment environment = mock(Environment.class);
    private final AuthSessionCookieService cookieService = mock(AuthSessionCookieService.class);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);

    private final AccountUserDetails adminDetails = new AccountUserDetails(
            EMAIL,
            "encoded-password",
            true,
            true,
            List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_USER")),
            0L);

    private JwtAuthenticationFilter filter() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService, userDetailsService, environment, cookieService, revocationService);
        ReflectionTestUtils.setField(filter, "adminGateEnabled", true);
        return filter;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void configureValidJwt(boolean gateProof) {
        when(revocationService.isRevoked(TOKEN)).thenReturn(false);
        when(jwtService.extractUsername(TOKEN)).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(adminDetails);
        when(jwtService.isTokenValid(TOKEN, adminDetails)).thenReturn(true);
        when(jwtService.isAdminGateVerified(TOKEN)).thenReturn(gateProof);
    }

    @Test
    void stripsAdminAuthorityFromAValidLegacyJwtWithoutGateProof() throws Exception {
        configureValidJwt(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(request.getCookies()).thenReturn(null);

        filter().doFilterInternal(request, response, chain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertFalse(authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        assertTrue(authorities.stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        verify(chain).doFilter(request, response);
    }

    @Test
    void preservesAdminAuthorityOnlyWhenSignedGateProofIsPresent() throws Exception {
        configureValidJwt(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(request.getCookies()).thenReturn(null);

        filter().doFilterInternal(request, response, chain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertTrue(authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        verify(chain).doFilter(request, response);
    }
}
