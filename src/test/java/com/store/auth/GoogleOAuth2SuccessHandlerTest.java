package com.store.auth;

import com.store.auth.service.AuthSessionCookieService;
import com.store.auth.service.GoogleOAuth2SuccessHandler;
import com.store.auth.service.GoogleUserProvisioningService;
import com.store.auth.service.JwtService;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2SuccessHandlerTest {

    @Mock private GoogleUserProvisioningService provisioningService;
    @Mock private JwtService jwtService;
    @Mock private AuthSessionCookieService cookieService;
    @Mock private Environment environment;
    @Mock private Authentication authentication;
    @Mock private OidcUser oidcUser;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    @InjectMocks private GoogleOAuth2SuccessHandler handler;

    @BeforeEach
    void configureRedirects() {
        ReflectionTestUtils.setField(
                handler, "frontendSuccessRedirect", "https://shop.example.com/buyer/profile");
        ReflectionTestUtils.setField(
                handler, "frontendFailureRedirect", "https://shop.example.com/login?error=google");
    }

    @Test
    void administratorNeverReceivesJwtOrSessionCookieFromGoogleOAuth() throws Exception {
        User admin = User.builder()
                .firstName("Store")
                .lastName("Admin")
                .email("admin@example.com")
                .password("existing-hash")
                .role(Role.builder().name("ROLE_ADMIN").build())
                .isEnabled(true)
                .emailVerified(true)
                .build();
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(provisioningService.provision(oidcUser)).thenReturn(admin);

        assertDoesNotThrow(() -> handler.onAuthenticationSuccess(request, response, authentication));

        verify(response).sendRedirect("https://shop.example.com/login?error=google");
        verify(response, never()).addHeader(eq("Set-Cookie"), anyString());
        verifyNoInteractions(jwtService, cookieService);
    }

    @Test
    void rejectedProvisioningRedirectsToGenericFailureWithoutMintingJwt() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(provisioningService.provision(oidcUser))
                .thenThrow(new com.store.common.exception.BusinessException(
                        "Administrator accounts must use the administrator login flow"));

        assertDoesNotThrow(() -> handler.onAuthenticationSuccess(request, response, authentication));

        verify(response).sendRedirect("https://shop.example.com/login?error=google");
        verifyNoInteractions(jwtService, cookieService);
    }
}
