package com.store.auth.service;

import com.store.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLoginGateServiceTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CloudflareTurnstileVerifier turnstileVerifier;

    private AdminLoginGateService service;

    @BeforeEach
    void setUp() {
        service = new AdminLoginGateService(passwordEncoder, turnstileVerifier);
        ReflectionTestUtils.setField(service, "adminGatePasswordHash", "$2a$10$encoded-admin-gate-password-hash");
    }

    @Test
    void rejectsMissingOrWrongSecondaryPasswordBeforeCallingTurnstile() {
        when(passwordEncoder.matches("wrong", "$2a$10$encoded-admin-gate-password-hash"))
                .thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> service.verify("wrong", "captcha-token"));

        verify(turnstileVerifier, never()).verify("captcha-token");
    }

    @Test
    void rejectsWhenTurnstileDoesNotVerify() {
        when(passwordEncoder.matches("correct", "$2a$10$encoded-admin-gate-password-hash"))
                .thenReturn(true);
        when(turnstileVerifier.verify("invalid-turnstile-token")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> service.verify("correct", "invalid-turnstile-token"));
    }

    @Test
    void acceptsOnlyWhenSecondaryPasswordAndTurnstileBothPass() {
        when(passwordEncoder.matches("correct", "$2a$10$encoded-admin-gate-password-hash"))
                .thenReturn(true);
        when(turnstileVerifier.verify("valid-turnstile-token")).thenReturn(true);

        assertDoesNotThrow(() -> service.verify("correct", "valid-turnstile-token"));
    }
}
