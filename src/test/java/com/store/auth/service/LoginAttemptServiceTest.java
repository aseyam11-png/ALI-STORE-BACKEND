package com.store.auth.service;

import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void failuresOlderThanTheRollingWindowDoNotAccumulateForever() {
        User user = User.builder()
                .firstName("Test")
                .lastName("Account")
                .email("stale@example.com")
                .password("hash")
                .isEnabled(true)
                .failedLoginAttempts(4)
                .lastFailedLoginAt(Instant.now().minus(Duration.ofMinutes(30)))
                .authVersion(0L)
                .build();
        user.setId(6L);

        when(userRepository.findByEmailIgnoreCaseForUpdate("stale@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new LoginAttemptService(userRepository).recordFailure("stale@example.com");

        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLoginLockedUntil());
        assertNotNull(user.getLastFailedLoginAt());
        assertTrue(user.getLastFailedLoginAt().isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void repeatedFailuresLockOnlyTheAccountAndFurtherFailuresDoNotExtendAnActiveLock() {
        User user = User.builder()
                .firstName("Test")
                .lastName("Account")
                .email("user@example.com")
                .password("hash")
                .isEnabled(true)
                .failedLoginAttempts(0)
                .authVersion(0L)
                .build();
        user.setId(5L);

        when(userRepository.findByEmailIgnoreCaseForUpdate("user@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        LoginAttemptService service = new LoginAttemptService(userRepository);

        for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
            service.recordFailure(" USER@example.com ");
        }

        assertEquals(LoginAttemptService.MAX_FAILED_ATTEMPTS, user.getFailedLoginAttempts());
        assertNotNull(user.getLoginLockedUntil());
        assertTrue(user.getLoginLockedUntil().isAfter(Instant.now()));
        assertTrue(service.isLocked("user@example.com"));

        Instant originalLockExpiry = user.getLoginLockedUntil();
        service.recordFailure("user@example.com");
        assertEquals(originalLockExpiry, user.getLoginLockedUntil());
        assertEquals(LoginAttemptService.MAX_FAILED_ATTEMPTS, user.getFailedLoginAttempts());
    }
}
