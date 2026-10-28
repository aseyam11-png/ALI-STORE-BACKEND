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
    private final JwtService jwtService=mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService=mock(CustomUserDetailsService.class);
    private final Environment environment=mock(Environment.class);
    private final AuthSessionCookieService cookieService=mock(AuthSessionCookieService.class);
    private final TokenRevocationService revocations=mock(TokenRevocationService.class);

    private JwtAuthenticationFilter filter() {
        JwtAuthenticationFilter filter=new JwtAuthenticationFilter(jwtService,userDetailsService,environment,cookieService,revocations);
        ReflectionTestUtils.setField(filter,"adminGateEnabled",true);
        return filter;
    }
    private AccountUserDetails admin() {
        return new AccountUserDetails("admin@example.invalid","{bcrypt}hash",true,true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),0L);
    }
    @AfterEach void clearContext(){ SecurityContextHolder.clearContext(); }

    private void run(String token, boolean proof) throws Exception {
        AccountUserDetails details=admin();
        when(revocations.isRevoked(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenReturn(details.getUsername());
        when(userDetailsService.loadUserByUsername(details.getUsername())).thenReturn(details);
        when(jwtService.isTokenValid(token,details)).thenReturn(true);
        when(jwtService.isAdminGateVerified(token)).thenReturn(proof);
        HttpServletRequest request=mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer "+token);
        HttpServletResponse response=mock(HttpServletResponse.class);
        FilterChain chain=mock(FilterChain.class);
        filter().doFilterInternal(request,response,chain);
        verify(chain).doFilter(request,response);
    }
    @Test void legacyTokenWithoutGateProofLosesAdminAuthority() throws Exception {
        run("legacy-token",false);
        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a->"ROLE_ADMIN".equals(a.getAuthority())));
    }
    @Test void signedGateProofPreservesAdminAuthority() throws Exception {
        run("gate-token",true);
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a->"ROLE_ADMIN".equals(a.getAuthority())));
    }
}
