package com.store.auth.service;

import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminGateJwtClaimTest {

    private JwtService jwtService() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", Encoders.BASE64.encode(new byte[32]));
        ReflectionTestUtils.setField(service, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(service, "issuer", "ali-store-backend");
        return service;
    }

    @Test
    void onlySignedTokenWithExplicitGateClaimIsAccepted() {
        JwtService service = jwtService();
        var admin = User.withUsername("admin@example.invalid")
                .password("unused")
                .authorities("ROLE_ADMIN")
                .build();

        String gated = service.generateToken(Map.of("admin_gate_verified", true), admin);
        String ordinary = service.generateToken(admin);

        assertTrue(service.isAdminGateVerified(gated));
        assertFalse(service.isAdminGateVerified(ordinary));
        assertFalse(service.isAdminGateVerified("not-a-jwt"));
    }
}
