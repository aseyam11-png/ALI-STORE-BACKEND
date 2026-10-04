package com.store.auth.controller;

import com.store.auth.dto.AuthResponse;
import com.store.auth.dto.EmailOtpChallengeResponse;
import com.store.auth.dto.EmailOtpResendRequest;
import com.store.auth.dto.EmailOtpVerificationRequest;
import com.store.auth.dto.LoginRequest;
import com.store.auth.dto.RegisterRequest;
import com.store.auth.service.AuthService;
import com.store.auth.service.AuthSessionCookieService;
import com.store.auth.service.TokenRevocationService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.response.ApiResponse;
import com.store.user.dto.MeResponse;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final Environment environment;
    private final TokenRevocationService tokenRevocationService;
    private final AuthSessionCookieService cookieService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse servletResponse) {
        AuthResponse response = authService.register(request);
        writeSessionCookieIfAuthenticated(servletResponse, response);

        HttpStatus status = response.isRequiresEmailVerification()
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        String message = response.isRequiresEmailVerification()
                ? "Verification code sent to your email"
                : "User registered successfully";

        return ResponseEntity.status(status)
                .body(ApiResponse.success(response, message));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse) {
        AuthResponse response = authService.login(request);
        writeSessionCookieIfAuthenticated(servletResponse, response);

        HttpStatus status = response.isRequiresEmailVerification()
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        String message = response.isRequiresEmailVerification()
                ? "Verification code sent to your email"
                : "Login successful";

        return ResponseEntity.status(status)
                .body(ApiResponse.success(response, message));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Valid @RequestBody EmailOtpVerificationRequest request,
            HttpServletResponse servletResponse) {
        AuthResponse response = authService.verifyEmailCode(
                request.getChallengeId(), request.getCode());
        writeSessionCookieIfAuthenticated(servletResponse, response);

        return ResponseEntity.ok(ApiResponse.success(
                response, "Email verification completed successfully"));
    }

    @PostMapping("/email/resend")
    public ResponseEntity<ApiResponse<EmailOtpChallengeResponse>> resendEmailCode(
            @Valid @RequestBody EmailOtpResendRequest request) {
        EmailOtpChallengeResponse response =
                authService.resendEmailCode(request.getChallengeId());

        return ResponseEntity.ok(ApiResponse.success(
                response, "A new verification code was sent to your email"));
    }

    @GetMapping("/google")
    public ResponseEntity<?> googleLogin() {
        if (clientRegistrationRepository.findByRegistrationId("google") == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Google login is not configured"));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create("/oauth2/authorization/google"))
                .build();
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<MeResponse>> session(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication is required"));
        }

        User user = userRepository.findByEmailIgnoreCase(
                        userDetails.getUsername().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MeResponse response = MeResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().getName())
                .build();

        return ResponseEntity.ok(
                ApiResponse.success(response, "Session retrieved successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse servletResponse) {
        String token = extractDecryptedCookieToken(request);
        if (token == null) {
            token = extractBearerToken(request);
        }

        tokenRevocationService.revoke(token);
        servletResponse.addHeader(
                "Set-Cookie", cookieService.clearCookie().toString());

        return ResponseEntity.ok(
                ApiResponse.success(null, "Logged out successfully"));
    }

    private void writeSessionCookieIfAuthenticated(
            HttpServletResponse servletResponse,
            AuthResponse response) {
        String token = response.getToken();
        if (token == null || token.isBlank()) {
            return;
        }

        long maxAgeSeconds = Math.max(0, jwtExpirationMs / 1000);
        servletResponse.addHeader(
                "Set-Cookie",
                cookieService.buildCookie(token, maxAgeSeconds).toString());
    }

    private String extractBearerToken(HttpServletRequest request) {
        String value = request.getHeader("Authorization");
        return value != null && value.startsWith("Bearer ")
                ? value.substring(7)
                : null;
    }

    private String extractDecryptedCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String cookieName = environment.getProperty(
                "app.auth.cookie-name",
                environment.getProperty(
                        "AUTH_COOKIE_NAME", "ali_store_session"));

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
