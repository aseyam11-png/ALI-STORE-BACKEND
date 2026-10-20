package com.store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for JWT secret strength and account-state validation.
 */
class JwtServiceTest {

    private JwtService newService(String base64Secret) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", base64Secret);
        ReflectionTestUtils.setField(service, "issuer", "ali-store-backend");
        ReflectionTestUtils.setField(service, "jwtExpiration", 60_000L);
        return service;
    }

    @Test
    void rejectsSecretBelow256Bits() {
        String weakSecret = Base64.getEncoder().encodeToString("too-short-key".getBytes());
        JwtService service = newService(weakSecret);
        assertThrows(IllegalStateException.class, service::validateSecretStrength);
    }

    @Test
    void acceptsSecretAtOrAbove256Bits() {
        String strongSecret = Base64.getEncoder().encodeToString("a".repeat(32).getBytes());
        JwtService service = newService(strongSecret);
        assertDoesNotThrow(service::validateSecretStrength);
    }

    @Test
    void rejectsNonBase64Secret() {
        JwtService service = newService("not-valid-base64!!!");
        assertThrows(IllegalStateException.class, service::validateSecretStrength);
    }

    @Test
    void rejectsJwtImmediatelyWhenAccountIsDisabled() {
        String secret = Base64.getEncoder().encodeToString("a".repeat(32).getBytes());
        JwtService service = newService(secret);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails enabled = new AccountUserDetails(
                "user@example.com", "hash", true, true, authorities, 0L);
        AccountUserDetails disabled = new AccountUserDetails(
                "user@example.com", "hash", false, true, authorities, 0L);

        String token = service.generateToken(enabled);

        assertTrue(service.isTokenValid(token, enabled));
        assertFalse(service.isTokenValid(token, disabled));
    }

    @Test
    void rejectsJwtWhenPasswordChangeAdvancesAccountVersion() {
        String secret = Base64.getEncoder().encodeToString("b".repeat(32).getBytes());
        JwtService service = newService(secret);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails original = new AccountUserDetails(
                "user@example.com", "hash", true, true, authorities, 0L);
        AccountUserDetails afterPasswordChange = new AccountUserDetails(
                "user@example.com", "new-hash", true, true, authorities, 1L);

        String token = service.generateToken(original);

        assertFalse(service.isTokenValid(token, afterPasswordChange));
    }
}
