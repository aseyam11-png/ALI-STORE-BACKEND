package com.store.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthSessionCookieService {

    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String VERSION = "v1";
    private static final byte[] AAD =
            "ALI-STORE-AUTH-COOKIE-V1".getBytes(StandardCharsets.UTF_8);

    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKeySpec encryptionKey;
    private boolean secureCookie;
    private String cookieName;
    private String cookieSameSite;
    private String cookieDomain;

    @PostConstruct
    void initialize() {
        cookieName = environment.getProperty(
                "app.auth.cookie-name", "ali_store_session");
        secureCookie = Boolean.parseBoolean(environment.getProperty(
                "app.auth.cookie-secure", "false"));

        String configuredSameSite = environment.getProperty(
                "app.auth.cookie-same-site", "");
        cookieSameSite = configuredSameSite == null || configuredSameSite.isBlank()
                ? "Strict"
                : configuredSameSite;
        cookieDomain = environment.getProperty(
                "app.auth.cookie-domain", "");

        if (isProd() && !secureCookie) {
            throw new IllegalStateException(
                    "AUTH_COOKIE_SECURE must be true in production");
        }
        if ("None".equalsIgnoreCase(cookieSameSite) && !secureCookie) {
            throw new IllegalStateException(
                    "SameSite=None requires Secure cookies");
        }

        String configuredKey = environment.getProperty(
                "AUTH_COOKIE_ENCRYPTION_KEY");
        if ((configuredKey == null || configuredKey.isBlank()) && isProd()) {
            throw new IllegalStateException(
                    "AUTH_COOKIE_ENCRYPTION_KEY must be configured separately in production");
        }
        if (configuredKey == null || configuredKey.isBlank()) {
            configuredKey = environment.getProperty("JWT_SECRET", "");
        }

        final byte[] rawKey;
        try {
            rawKey = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "AUTH_COOKIE_ENCRYPTION_KEY must be valid Base64", ex);
        }

        if (rawKey.length < AES_KEY_BYTES) {
            throw new IllegalStateException(
                    "AUTH_COOKIE_ENCRYPTION_KEY must decode to at least 32 bytes");
        }

        encryptionKey = new SecretKeySpec(
                Arrays.copyOf(rawKey, AES_KEY_BYTES), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot encrypt an empty authentication token");
        }

        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));

            return VERSION + "."
                    + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(iv)
                    + "."
                    + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Authentication cookie encryption failed", ex);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            throw new SecurityException("Authentication cookie is empty");
        }

        String[] parts = value.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new SecurityException(
                    "Authentication cookie format is invalid");
        }

        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);

            if (iv.length != GCM_IV_BYTES) {
                throw new SecurityException(
                        "Authentication cookie nonce is invalid");
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);

            return new String(
                    cipher.doFinal(ciphertext),
                    StandardCharsets.UTF_8);
        } catch (SecurityException ex) {
            throw ex;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new SecurityException(
                    "Authentication cookie cannot be authenticated", ex);
        }
    }

    public ResponseCookie buildCookie(String jwt, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder =
                ResponseCookie.from(cookieName, encrypt(jwt))
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite(cookieSameSite)
                        .path("/")
                        .maxAge(Math.max(0, maxAgeSeconds));

        if (!cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }

    public ResponseCookie clearCookie() {
        ResponseCookie.ResponseCookieBuilder builder =
                ResponseCookie.from(cookieName, "")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite(cookieSameSite)
                        .path("/")
                        .maxAge(0);

        if (!cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }

    private boolean isProd() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }
}
