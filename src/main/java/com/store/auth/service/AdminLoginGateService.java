package com.store.auth.service;

import com.store.auth.dto.LoginRequest;
import com.store.common.exception.ServiceUnavailableException;
import com.store.user.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AdminLoginGateService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final PasswordEncoder passwordEncoder;
    private final TurnstileVerifier turnstileVerifier;

    @Value("${app.security.admin-gate.enabled:false}")
    private boolean enabled;

    @Value("${app.security.admin-gate.password-hash:}")
    private String passwordHash;

    @Value("${app.security.admin-gate.turnstile-secret:}")
    private String turnstileSecret;

    @Value("${app.security.admin-gate.turnstile-hostname:}")
    private String expectedHostname;

    @PostConstruct
    void validateProductionConfiguration() {
        if (enabled
                && (isBlank(passwordHash)
                || !passwordHash.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}")
                || isBlank(turnstileSecret)
                || isBlank(expectedHostname))) {
            throw new IllegalStateException(
                    "A valid BCrypt cost-12 ADMIN_GATE_PASSWORD_HASH, TURNSTILE_SECRET_KEY, and "
                            + "TURNSTILE_HOSTNAME must be configured before enabling the administrator gate");
        }
    }

    /**
     * Returns true only when the account is an administrator and both the second
     * password and the server-verified Turnstile proof succeed.
     */
    public boolean verifyIfAdmin(User user, LoginRequest request, String clientIp) {
        if (user.getRole() == null || !ADMIN_ROLE.equals(user.getRole().getName())) {
            return false;
        }

        if (!enabled) {
            // Local/development admin sessions do not receive production gate proof.
            return false;
        }

        if (isBlank(passwordHash) || isBlank(turnstileSecret) || isBlank(expectedHostname)) {
            throw new ServiceUnavailableException(
                    "Administrator security gate is not configured. Contact the system operator.");
        }

        String captchaToken = request.getCaptchaToken();
        String secondPassword = request.getAdminGatePassword();

        // Do not disclose which part of the secondary check failed.
        if (isBlank(captchaToken)
                || !turnstileVerifier.verify(captchaToken, clientIp, turnstileSecret, expectedHostname)
                || isBlank(secondPassword)
                || secondPassword.getBytes(StandardCharsets.UTF_8).length < 16
                || secondPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !safePasswordMatches(secondPassword, passwordHash)) {
            throw new BadCredentialsException("Invalid credentials or security verification failed");
        }

        return true;
    }

    private boolean safePasswordMatches(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException ex) {
            // Malformed or unsupported hashes fail closed rather than producing a 500.
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
