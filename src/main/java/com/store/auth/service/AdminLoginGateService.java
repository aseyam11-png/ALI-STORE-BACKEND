package com.store.auth.service;

import com.store.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Enforces the independent administrator-only password and server-verified
 * Turnstile challenge after primary credentials have authenticated the account.
 */
@Service
public class AdminLoginGateService {

    private static final String GENERIC_FAILURE = "Invalid email or password";

    private final PasswordEncoder passwordEncoder;
    private final CloudflareTurnstileVerifier turnstileVerifier;

    @Value("${app.auth.admin-gate-password-hash:}")
    private String adminGatePasswordHash;

    public AdminLoginGateService(
            PasswordEncoder passwordEncoder,
            CloudflareTurnstileVerifier turnstileVerifier) {
        this.passwordEncoder = passwordEncoder;
        this.turnstileVerifier = turnstileVerifier;
    }

    public void verify(String adminGatePassword, String turnstileToken) {
        if (!StringUtils.hasText(adminGatePassword)
                || !StringUtils.hasText(adminGatePasswordHash)) {
            throw new UnauthorizedException(GENERIC_FAILURE);
        }

        final boolean passwordMatches;
        try {
            passwordMatches = passwordEncoder.matches(adminGatePassword, adminGatePasswordHash);
        } catch (IllegalArgumentException ex) {
            // A malformed/unconfigured hash must fail closed, not permit sign-in.
            throw new UnauthorizedException(GENERIC_FAILURE);
        }

        if (!passwordMatches || !turnstileVerifier.verify(turnstileToken)) {
            throw new UnauthorizedException(GENERIC_FAILURE);
        }
    }
}
