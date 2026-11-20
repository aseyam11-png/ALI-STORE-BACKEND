package com.store.auth.service;

import com.store.common.exception.BusinessException;
import com.store.user.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final GoogleUserProvisioningService provisioningService;
    private final JwtService jwtService;
    private final AuthSessionCookieService cookieService;
    private final Environment environment;

    @Value("${app.auth.frontend-success-redirect:http://localhost:3000/buyer/profile}")
    private String frontendSuccessRedirect;

    @Value("${app.auth.frontend-failure-redirect:http://localhost:3000/login?error=google}")
    private String frontendFailureRedirect;

    /**
     * Production must never fall back to a localhost callback. The prod profile
     * requires both values and this guard rejects accidental HTTP/local URLs too.
     */
    @PostConstruct
    void validateProductionRedirects() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            validateProductionRedirect(frontendSuccessRedirect, "FRONTEND_AUTH_SUCCESS_REDIRECT");
            validateProductionRedirect(frontendFailureRedirect, "FRONTEND_AUTH_FAILURE_REDIRECT");
        }
    }

    private static void validateProductionRedirect(String redirect, String propertyName) {
        URI uri;
        try {
            uri = URI.create(redirect);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(propertyName + " must be an absolute HTTPS URL to the deployed frontend.", ex);
        }

        String host = uri.getHost();
        String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
        boolean localHost = normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.equals("::1")
                || normalizedHost.equals("0.0.0.0")
                || normalizedHost.startsWith("127.");

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || normalizedHost.isBlank()
                || uri.getUserInfo() != null
                || localHost) {
            throw new IllegalStateException(
                    propertyName + " must be an absolute HTTPS URL to the deployed frontend, not localhost.");
        }
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new ServletException("Google authentication did not return an OIDC user");
        }

        final User user;
        try {
            user = provisioningService.provision(oidcUser);
        } catch (BusinessException ex) {
            // Expected policy failures (disabled/unverified accounts or forbidden
            // administrator Google login) must not mint a cookie or leak details.
            redirectToFailure(request, response);
            return;
        }

        // Defense in depth: even if provisioning policy regresses, never mint an
        // administrator JWT from Google OAuth while the secondary admin gate is absent.
        if (isAdministrator(user)) {
            redirectToFailure(request, response);
            return;
        }

        AccountUserDetails userDetails = new AccountUserDetails(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getIsEnabled()),
                true,
                Collections.singletonList(
                        new SimpleGrantedAuthority(user.getRole().getName())),
                user.getAuthVersion());

        String jwt = jwtService.generateToken(userDetails);
        long maxAgeSeconds = Math.max(0, jwtService.extractExpiration(jwt).toInstant().getEpochSecond()
                - java.time.Instant.now().getEpochSecond());

        response.addHeader("Set-Cookie", cookieService.buildCookie(jwt, maxAgeSeconds).toString());

        invalidateSession(request);
        response.sendRedirect(frontendSuccessRedirect);
    }

    private boolean isAdministrator(User user) {
        return user != null
                && user.getRole() != null
                && ADMIN_ROLE.equalsIgnoreCase(user.getRole().getName());
    }

    private void redirectToFailure(HttpServletRequest request, HttpServletResponse response) throws IOException {
        invalidateSession(request);
        response.sendRedirect(frontendFailureRedirect);
    }

    private void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
