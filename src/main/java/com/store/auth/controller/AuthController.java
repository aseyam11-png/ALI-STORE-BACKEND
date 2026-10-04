package com.store.auth.controller;

import com.store.auth.dto.AuthResponse;
import com.store.auth.dto.LoginRequest;
import com.store.auth.dto.RegisterRequest;
import com.store.auth.service.AuthService;
import com.store.auth.service.AuthSessionCookieService;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.response.ApiResponse;
import com.store.user.dto.MeResponse;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final AuthSessionCookieService cookieService;

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
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse servletResponse) {
        servletResponse.addHeader("Set-Cookie", cookieService.clearCookie().toString());
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private void writeSessionCookie(HttpServletResponse servletResponse, String token) {
        servletResponse.addHeader("Set-Cookie", cookieService.buildCookie(token, 24 * 60 * 60).toString());
    }

}
