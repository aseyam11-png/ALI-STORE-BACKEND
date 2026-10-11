package com.store.auth.service;

import com.store.auth.dto.LoginRequest;
import com.store.common.exception.ServiceUnavailableException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminLoginGateServiceTest {

    private final AdminLoginGateService gate =
            new AdminLoginGateService(new BCryptPasswordEncoder(4));

    @Test
    void normalCustomerDoesNotNeedTheAdminGate() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        User user = User.builder()
                .email("customer@example.invalid")
                .role(Role.builder().name("ROLE_USER").build())
                .build();

        assertFalse(gate.verifyIfAdmin(user, new LoginRequest(), "198.51.100.20"));
    }

    @Test
    void productionAdminGateFailsClosedWhenSecretsAreMissing() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        ReflectionTestUtils.setField(gate, "passwordHash", "");
        ReflectionTestUtils.setField(gate, "turnstileSecret", "");
        ReflectionTestUtils.setField(gate, "expectedHostname", "");
        User user = User.builder()
                .email("admin@example.invalid")
                .role(Role.builder().name("ROLE_ADMIN").build())
                .build();

        assertThrows(ServiceUnavailableException.class,
                () -> gate.verifyIfAdmin(user, new LoginRequest(), "198.51.100.20"));
    }

    @Test
    void disabledGateDoesNotCreateGateVerificationProof() {
        ReflectionTestUtils.setField(gate, "enabled", false);
        User user = User.builder()
                .email("admin@example.invalid")
                .role(Role.builder().name("ROLE_ADMIN").build())
                .build();

        assertFalse(gate.verifyIfAdmin(user, new LoginRequest(), "198.51.100.20"));
    }
}
