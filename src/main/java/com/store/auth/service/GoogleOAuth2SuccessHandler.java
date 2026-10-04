package com.store.auth.service;

import com.store.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleUserProvisioningService provisioningService;
    private final JwtService jwtService;
    private final AuthSessionCookieService cookieService;

    @Value("${app.auth.frontend-success-redirect:http://localhost:3000/buyer/profile}")
    private String frontendSuccessRedirect;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new ServletException("Google authentication did not return an OIDC user");
        }

        User user = provisioningService.provision(oidcUser);
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getIsEnabled()),
                true,
                true,
                true,
                java.util.Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                user.getRole().getName()))
        );

        String jwt = jwtService.generateToken(userDetails);
        long maxAgeSeconds = Math.max(0, jwtService.extractExpiration(jwt).toInstant().getEpochSecond()
                - java.time.Instant.now().getEpochSecond());

        response.addHeader("Set-Cookie", cookieService.buildCookie(jwt, maxAgeSeconds).toString());

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(frontendSuccessRedirect);
    }
}
