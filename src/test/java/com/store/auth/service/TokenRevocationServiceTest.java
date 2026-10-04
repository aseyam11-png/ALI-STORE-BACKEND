package com.store.auth.service;

import com.store.auth.entity.RevokedToken;
import com.store.auth.repository.RevokedTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private RevokedTokenRepository repository;

    @Mock
    private JwtService jwtService;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(repository, jwtService);
    }

    @Test
    void revoke_storesNonExpiredTokenAsHash() {
        String token = "jwt-token";
        Instant expiry = Instant.now().plusSeconds(3600);
        when(jwtService.extractExpiration(token))
                .thenReturn(java.util.Date.from(expiry));
        when(repository.existsByTokenHashAndExpiresAtAfter(
                any(String.class), any(LocalDateTime.class)))
                .thenReturn(false);
        when(repository.save(any(RevokedToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.revoke(token);

        verify(repository).save(argThat(saved ->
                saved.getTokenHash() != null
                        && saved.getTokenHash().length() == 64
                        && saved.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
        ));
    }

    @Test
    void revoke_doesNotPersistExpiredToken() {
        String token = "expired-token";
        when(jwtService.extractExpiration(token))
                .thenReturn(java.util.Date.from(Instant.now().minusSeconds(1)));

        service.revoke(token);

        verify(repository, never()).save(any(RevokedToken.class));
    }

    @Test
    void isRevoked_delegatesToHashedTokenLookup() {
        when(repository.existsByTokenHashAndExpiresAtAfter(
                any(String.class), any(LocalDateTime.class)))
                .thenReturn(true);

        assertTrue(service.isRevoked("jwt-token"));
        verify(repository).existsByTokenHashAndExpiresAtAfter(
                argThat(hash -> hash.length() == 64),
                any(LocalDateTime.class));
    }
}
