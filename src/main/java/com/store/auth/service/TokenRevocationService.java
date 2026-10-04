package com.store.auth.service;

import com.store.auth.entity.RevokedToken;
import com.store.auth.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository repository;
    private final JwtService jwtService;

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;

        try {
            LocalDateTime expiresAt = jwtService.extractExpiration(token)
                    .toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            if (!expiresAt.isAfter(now)) return;

            String hash = sha256(token);
            if (!repository.existsByTokenHashAndExpiresAtAfter(hash, now)) {
                repository.save(RevokedToken.builder()
                        .tokenHash(hash)
                        .expiresAt(expiresAt)
                        .revokedAt(now)
                        .build());
            }
        } catch (RuntimeException ignored) {
            // Logout remains idempotent even when the supplied token is already invalid.
        }
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) return false;
        return repository.existsByTokenHashAndExpiresAtAfter(
                sha256(token), LocalDateTime.now(ZoneOffset.UTC));
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanup() {
        repository.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
    }

    private String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
