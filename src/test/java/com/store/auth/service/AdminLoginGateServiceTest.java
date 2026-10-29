package com.store.auth.service;

import com.store.auth.dto.LoginRequest;
import com.store.common.exception.ServiceUnavailableException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminLoginGateServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    @Mock private TurnstileVerifier turnstileVerifier;
    private AdminLoginGateService gate;

    @BeforeEach
    void setUp() {
        gate = new AdminLoginGateService(encoder, turnstileVerifier);
    }

    @Test
    void normalCustomerDoesNotNeedTheAdminGate() {
        enableGateWithSecrets();
        User customer = user("ROLE_USER");
        assertFalse(gate.verifyIfAdmin(customer, new LoginRequest(), "198.51.100.20"));
        verifyNoInteractions(turnstileVerifier);
    }

    @Test
    void productionAdminGateFailsClosedWhenSecretsAreMissing() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        User admin = user("ROLE_ADMIN");
        assertThrows(ServiceUnavailableException.class,
                () -> gate.verifyIfAdmin(admin, new LoginRequest(), "198.51.100.20"));
        verifyNoInteractions(turnstileVerifier);
    }

    @Test
    void productionProfileCannotStartWithAdministratorGateDisabled() {
        ReflectionTestUtils.setField(gate, "enabled", false);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(gate, "environment", environment);

        IllegalStateException error = assertThrows(
                IllegalStateException.class, gate::validateProductionConfiguration);
        assertTrue(error.getMessage().contains("must remain enabled in the prod profile"));
    }

    @Test
    void disabledGateDoesNotCreateGateVerificationProof() {
        ReflectionTestUtils.setField(gate, "enabled", false);
        User admin = user("ROLE_ADMIN");
        assertFalse(gate.verifyIfAdmin(admin, validRequest("second-password-16chars"), "198.51.100.20"));
        verifyNoInteractions(turnstileVerifier);
    }

    @Test
    void validSecondPasswordAndTurnstileProofAuthorizeGate() {
        enableGateWithSecrets();
        ReflectionTestUtils.setField(gate, "passwordHash", encoder.encode("second-password-16chars"));
        when(turnstileVerifier.verify("captcha-token", "198.51.100.20", "turnstile-secret", "store.example.com"))
                .thenReturn(true);

        assertTrue(gate.verifyIfAdmin(user("ROLE_ADMIN"),
                validRequest("second-password-16chars"), "198.51.100.20"));
        verify(turnstileVerifier).verify(
                "captcha-token", "198.51.100.20", "turnstile-secret", "store.example.com");
    }

    @Test
    void failedTurnstileProofRejectsCorrectSecondPassword() {
        enableGateWithSecrets();
        ReflectionTestUtils.setField(gate, "passwordHash", encoder.encode("second-password-16chars"));
        when(turnstileVerifier.verify("captcha-token", "198.51.100.20", "turnstile-secret", "store.example.com"))
                .thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> gate.verifyIfAdmin(user("ROLE_ADMIN"),
                        validRequest("second-password-16chars"), "198.51.100.20"));
    }

    @Test
    void incorrectSecondPasswordIsRejectedAfterTurnstileVerification() {
        enableGateWithSecrets();
        ReflectionTestUtils.setField(gate, "passwordHash", encoder.encode("second-password-16chars"));
        when(turnstileVerifier.verify("captcha-token", "198.51.100.20", "turnstile-secret", "store.example.com"))
                .thenReturn(true);

        assertThrows(BadCredentialsException.class,
                () -> gate.verifyIfAdmin(user("ROLE_ADMIN"),
                        validRequest("wrong-second-password"), "198.51.100.20"));
    }

    private void enableGateWithSecrets() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        ReflectionTestUtils.setField(gate, "turnstileSecret", "turnstile-secret");
        ReflectionTestUtils.setField(gate, "expectedHostname", "store.example.com");
    }

    private LoginRequest validRequest(String secondPassword) {
        return LoginRequest.builder()
                .email("admin@example.invalid")
                .password("primary-password")
                .adminGatePassword(secondPassword)
                .captchaToken("captcha-token")
                .build();
    }

    private User user(String roleName) {
        return User.builder()
                .email("admin@example.invalid")
                .isEnabled(true)
                .role(Role.builder().name(roleName).build())
                .build();
    }
}
