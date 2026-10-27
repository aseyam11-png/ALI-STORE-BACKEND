package com.store.common.security;

import com.store.auth.service.AccountUserDetails;
import com.store.auth.service.AuthSessionCookieService;
import com.store.auth.service.CustomUserDetailsService;
import com.store.auth.service.JwtService;
import com.store.auth.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterSessionVersionTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private Environment environment;

    @Mock
    private AuthSessionCookieService cookieService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clearsAuthenticationWhenPrincipalHasNoAccountVersion()
            throws Exception {

        User principal = new User(
                "ali@example.com",
                "irrelevant",
                List.of()
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        AccountUserDetails current = new AccountUserDetails(
                "ali@example.com",
                "encoded-password",
                true,
                true,
                List.of(),
                3L
        );

        when(userDetailsService.loadUserByUsername("ali@example.com"))
                .thenReturn(current);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(userDetailsService).loadUserByUsername("ali@example.com");
        verify(chain).doFilter(request, response);
    }

    @Test
    void clearsAuthenticationWhenAccountVersionHasChanged()
            throws Exception {

        AccountUserDetails previous = new AccountUserDetails(
                "ali@example.com",
                "encoded-password",
                true,
                true,
                List.of(),
                2L
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        previous,
                        null,
                        previous.getAuthorities()
                );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        AccountUserDetails current = new AccountUserDetails(
                "ali@example.com",
                "encoded-password",
                true,
                true,
                List.of(),
                3L
        );

        when(userDetailsService.loadUserByUsername("ali@example.com"))
                .thenReturn(current);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void preservesAuthenticationWhenAccountVersionMatches()
            throws Exception {

        AccountUserDetails principal = new AccountUserDetails(
                "ali@example.com",
                "encoded-password",
                true,
                true,
                List.of(),
                3L
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        AccountUserDetails current = new AccountUserDetails(
                "ali@example.com",
                "encoded-password",
                true,
                true,
                List.of(),
                3L
        );

        when(userDetailsService.loadUserByUsername("ali@example.com"))
                .thenReturn(current);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertSame(
                authentication,
                SecurityContextHolder.getContext().getAuthentication()
        );

        verify(chain).doFilter(request, response);
    }
}
