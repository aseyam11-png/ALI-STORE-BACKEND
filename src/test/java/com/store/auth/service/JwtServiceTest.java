package com.store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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
    void accountLockoutDoesNotRevokeAnAlreadyIssuedJwt() {
        String secret = Base64.getEncoder().encodeToString("c".repeat(32).getBytes());
        JwtService service = newService(secret);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails unlocked = new AccountUserDetails(
                "user@example.com", "hash", true, true, authorities, 0L);
        AccountUserDetails locked = new AccountUserDetails(
                "user@example.com", "hash", true, false, authorities, 0L);

        String token = service.generateToken(unlocked);

        assertTrue(service.isTokenValid(token, locked),
                "Login lockout is a control against new password authentication, not JWT revocation");
    }

    @Test
    void invalidTokenInputsDoNotCauseNullPointerExceptions() {
        String secret = Base64.getEncoder().encodeToString("d".repeat(32).getBytes());
        JwtService service = newService(secret);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails user = new AccountUserDetails(
                "user@example.com", "hash", true, true, authorities, 0L);

        assertFalse(service.isTokenValid(null, user));
        assertFalse(service.isTokenValid("  ", user));
        assertFalse(service.isTokenValid("not-parsed-because-user-details-is-null", null));
    }

    @Test
    void rejectsSignedJwtWithoutExpirationInsteadOfThrowingNullPointerException() {
        String secret = Base64.getEncoder().encodeToString("f".repeat(32).getBytes());
        JwtService service = newService(secret);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        AccountUserDetails user = new AccountUserDetails(
                "user@example.com", "hash", true, true, authorities, 0L);
        String token = Jwts.builder()
                .subject("user@example.com")
                .issuer("ali-store-backend")
                .issuedAt(new Date())
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)))
                .compact();

        assertFalse(service.isTokenValid(token, user));
    }

    @Test
    void rejectsJwtWhenAuthVersionClaimHasUnexpectedType() {
        String secret = Base64.getEncoder().encodeToString("e".repeat(32).getBytes());
        JwtService service = newService(secret);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var legacyDetails = new org.springframework.security.core.userdetails.User(
                "user@example.com", "hash", true, true, true, true, authorities);
        AccountUserDetails currentDetails = new AccountUserDetails(
                "user@example.com", "hash", true, true, authorities, 0L);

        String token = service.generateToken(Map.of("av", "not-a-number"), legacyDetails);

        assertFalse(service.isTokenValid(token, currentDetails));
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
