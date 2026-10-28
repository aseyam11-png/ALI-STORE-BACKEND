package com.store.auth.service;

import com.store.auth.dto.LoginRequest;
import com.store.common.exception.ServiceUnavailableException;
import com.store.common.exception.UnauthorizedException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminLoginGateServiceTest {
    private final AdminLoginGateService gate = new AdminLoginGateService(new BCryptPasswordEncoder(4));

    @Test
    void normalCustomerDoesNotNeedTheAdminGate() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        User user = User.builder().email("customer@example.invalid")
                .role(Role.builder().name("ROLE_USER").build()).build();
        assertFalse(gate.verifyIfAdmin(user, new LoginRequest(), "198.51.100.20"));
    }

    @Test
    void enabledGateFailsClosedWhenSecretsAreMissing() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        ReflectionTestUtils.setField(gate, "passwordHash", "");
        ReflectionTestUtils.setField(gate, "turnstileSecret", "");
        ReflectionTestUtils.setField(gate, "expectedHostname", "");
        User user = User.builder().email("admin@example.invalid")
                .role(Role.builder().name("ROLE_ADMIN").build()).build();
        assertThrows(ServiceUnavailableException.class,
                () -> gate.verifyIfAdmin(user, new LoginRequest(), "198.51.100.20"));
    }

    @Test
    void adminCannotPassWithoutSecondPasswordAndCaptcha() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        ReflectionTestUtils.setField(gate, "passwordHash", "$2a$12$12345678901234567890123456789012345678901234567890123");
        ReflectionTestUtils.setField(gate, "turnstileSecret", "test-secret");
        ReflectionTestUtils.setField(gate, "expectedHostname", "store.example.com");
        User user = User.builder().email("admin@example.invalid")
                .role(Role.builder().name("ROLE_ADMIN").build()).build();
        assertThrows(UnauthorizedException.class,
                () -> gate.verifyIfAdmin(user, new LoginRequest(), "198.51.100.20"));
    }

    @Test
    void disabledGateDoesNotCreateGateProof() {
        ReflectionTestUtils.setField(gate, "enabled", false);
        User user = User.builder().email("admin@example.invalid")
                .role(Role.builder().name("ROLE_ADMIN").build()).build();
        assertFalse(gate.verifyIfAdmin(user, new LoginRequest(), "198.51.100.20"));
    }
}
