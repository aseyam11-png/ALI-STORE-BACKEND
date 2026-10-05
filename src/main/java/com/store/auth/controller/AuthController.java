package com.store.auth.controller;

import com.store.auth.dto.AuthResponse;
import com.store.auth.dto.LoginRequest;
import com.store.auth.dto.RegisterRequest;
import com.store.auth.service.AuthService;
import com.store.auth.service.TokenRevocationService;
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
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final Environment environment;
    private final TokenRevocationService tokenRevocationService;

    @org.springframework.beans.factory.annotation.Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse servletResponse) {
        AuthResponse response = authService.register(request);
        writeSessionCookie(servletResponse, response.getToken());
        return ResponseEntity.ok(ApiResponse.success(response, "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse) {
        AuthResponse response = authService.login(request);
        writeSessionCookie(servletResponse, response.getToken());
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(ApiResponse.success(csrfToken.getToken(), "CSRF token generated"));
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<MeResponse>> session(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication is required"));
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MeResponse response = MeResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().getName())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Session retrieved successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse servletResponse) {
        String token = extractCookieToken(request);
        if (token == null) token = extractBearerToken(request);
        tokenRevocationService.revoke(token);

        servletResponse.addHeader("Set-Cookie", buildCookie("", 0).toString());
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String value = request.getHeader("Authorization");
        return value != null && value.startsWith("Bearer ") ? value.substring(7) : null;
    }

    private String extractCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        String cookieName = environment.getProperty(
                "app.auth.cookie-name",
                environment.getProperty("AUTH_COOKIE_NAME", "ali_store_session"));
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void writeSessionCookie(HttpServletResponse servletResponse, String token) {
        servletResponse.addHeader("Set-Cookie", buildCookie(token, Math.max(0, jwtExpirationMs / 1000)).toString());
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        boolean secure = Boolean.parseBoolean(environment.getProperty(
                "app.auth.cookie-secure",
                environment.getProperty("AUTH_COOKIE_SECURE", "false")));

        String name = environment.getProperty(
                "app.auth.cookie-name",
                environment.getProperty("AUTH_COOKIE_NAME", "ali_store_session"));

        String sameSite = environment.getProperty(
                "app.auth.cookie-same-site",
                environment.getProperty("AUTH_COOKIE_SAME_SITE", secure ? "None" : "Lax"));

        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
