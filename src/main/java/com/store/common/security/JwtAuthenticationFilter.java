package com.store.common.security;

import com.store.auth.service.AccountVersionAware;
import com.store.auth.service.AuthSessionCookieService;
import com.store.auth.service.TokenRevocationService;
import com.store.auth.service.CustomUserDetailsService;
import com.store.auth.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final Environment environment;
    private final AuthSessionCookieService cookieService;
    private final TokenRevocationService tokenRevocationService;

    @Value("${app.security.admin-gate.enabled:false}")
    private boolean adminGateEnabled;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String jwt = extractBearerToken(request);
        if (jwt == null) {
            jwt = extractCookieToken(request);
        }

        if (jwt != null) {
            // A presented token is authoritative: do not let an older session
            // Authentication bypass database-backed enabled/version checks.
            try {
                if (tokenRevocationService.isRevoked(jwt)) {
                    SecurityContextHolder.clearContext();
                } else {
                    String userEmail = jwtService.extractUsername(jwt);
                    if (userEmail == null || userEmail.isBlank()) {
                        SecurityContextHolder.clearContext();
                    } else {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
                        if (jwtService.isTokenValid(jwt, userDetails)) {
                            var grantedAuthorities = userDetails.getAuthorities();
                            boolean isAdministrator = grantedAuthorities.stream()
                                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                            if (adminGateEnabled && isAdministrator && !jwtService.isAdminGateVerified(jwt)) {
                                grantedAuthorities = grantedAuthorities.stream()
                                        .filter(authority -> !"ROLE_ADMIN".equals(authority.getAuthority()))
                                        .toList();
                            }
                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            grantedAuthorities
                                    );
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        } else {
                            SecurityContextHolder.clearContext();
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException | SecurityException ex) {
                SecurityContextHolder.clearContext();
            }
        } else {
            // OAuth2 can establish a server-side SecurityContext. Re-check local account
            // state even when no JWT is presented, so disabling a local account also
            // invalidates a surviving session instead of waiting for its original expiry.
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {
                Object principal = authentication.getPrincipal();
                // Google OAuth success deliberately creates an encrypted JWT cookie and
                // invalidates the servlet session. A surviving OIDC-only context has no
                // account-version claim, so it cannot prove it predates a password reset;
                // clear it and require the database-checked JWT session instead.
                if (principal instanceof OidcUser) {
                    SecurityContextHolder.clearContext();
                } else {
                    try {
                        UserDetails current = userDetailsService.loadUserByUsername(authentication.getName());
                        boolean enabled = current.isEnabled();
                        boolean versionMatches;
                        if (current instanceof AccountVersionAware latest) {
                            // A version-aware local account must not accept an older principal
                            // that cannot prove which account version authenticated it.
                            versionMatches = principal instanceof AccountVersionAware previous
                                    && previous.getAuthVersion() == latest.getAuthVersion();
                        } else {
                            // Preserve compatibility only when neither side carries a version.
                            versionMatches = !(principal instanceof AccountVersionAware);
                        }
                        boolean sessionHasAdminAuthority = authentication.getAuthorities().stream()
                                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                        if (!enabled || !versionMatches || (adminGateEnabled && sessionHasAdminAuthority)) {
                            // A server-side session has no signed admin-gate JWT proof, so never
                            // preserve ROLE_ADMIN through the session-only compatibility path.
                            SecurityContextHolder.clearContext();
                        }
                    } catch (UsernameNotFoundException ex) {
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : null;
    }

    private String extractCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String cookieName = environment.getProperty(
                "app.auth.cookie-name",
                environment.getProperty("AUTH_COOKIE_NAME", "ali_store_session"));

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                try {
                    return cookieService.decrypt(cookie.getValue());
                } catch (SecurityException ignored) {
                    return null;
                }
            }
        }

        return null;
    }
}
