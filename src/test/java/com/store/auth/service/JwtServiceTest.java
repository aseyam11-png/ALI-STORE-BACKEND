package com.store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plain unit test (no Spring context) for JwtService's startup-time secret-strength
 * check. Directly exercises validateSecretStrength() via reflection rather than booting
 * a full context with a bad JWT_SECRET, which would be slower and less precise about
 * exactly what's being asserted.
 */
class JwtServiceTest {

    private JwtService newService(String base64Secret) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", base64Secret);
        ReflectionTestUtils.setField(service, "issuer", "ali-store-backend");
        return service;
    }

    @Test
    void rejectsSecretBelow256Bits() {
        String weakSecret = Base64.getEncoder().encodeToString("too-short-key".getBytes()); // well under 32 bytes
        JwtService service = newService(weakSecret);

        assertThrows(IllegalStateException.class, service::validateSecretStrength);
    }

    @Test
    void acceptsSecretAtOrAbove256Bits() {
        String strongSecret = Base64.getEncoder().encodeToString("a".repeat(32).getBytes()); // exactly 32 bytes
        JwtService service = newService(strongSecret);

        assertDoesNotThrow(service::validateSecretStrength);
    }

    @Test
    void rejectsNonBase64Secret() {
        JwtService service = newService("not-valid-base64!!!");

        assertThrows(IllegalStateException.class, service::validateSecretStrength);
    }
}
