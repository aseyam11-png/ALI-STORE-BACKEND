package com.store.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class AuthSessionCookieServiceTest {

    private AuthSessionCookieService service;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "JWT_SECRET",
                        "dGVzdC1vbmx5LWp3dC1zaWduaW5nLXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24tdXNlLTEyMzQ1Njc4OTA=")
                .withProperty("app.auth.cookie-secure", "false")
                .withProperty("app.auth.cookie-same-site", "Lax");

        service = new AuthSessionCookieService(environment);
        service.initialize();
    }

    @Test
    void encryptedCookieRoundTripsWithoutExposingPlaintext() {
        String token = "eyJhbGciOiJIUzI1NiJ9.test.jwt";
        String encoded = service.encrypt(token);

        assertNotEquals(token, encoded);
        assertTrue(encoded.startsWith("v1."));
        assertEquals(token, service.decrypt(encoded));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String encoded = service.encrypt("sensitive-jwt-token");
        String[] parts = encoded.split("\\.", -1);
        String ciphertext = parts[2];
        char first = ciphertext.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "."
                + replacement + ciphertext.substring(1);

        assertThrows(SecurityException.class, () -> service.decrypt(tampered));
    }

    @Test
    void malformedCookieIsRejected() {
        assertThrows(SecurityException.class, () -> service.decrypt("not-a-session-cookie"));
        assertThrows(SecurityException.class, () -> service.decrypt("v1.invalid.invalid"));
    }
}
