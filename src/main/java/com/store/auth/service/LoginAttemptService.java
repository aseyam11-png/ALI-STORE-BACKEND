package com.store.auth.service;

import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Database-backed account lockout. Failed attempts are committed in a separate
 * transaction so the subsequent authentication exception cannot roll them back.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .map(User::getLoginLockedUntil)
                .filter(lockedUntil -> lockedUntil != null && lockedUntil.isAfter(now))
                .isPresent();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail).orElse(null);
        if (user == null) {
            return;
        }

        Instant now = Instant.now();
        Instant lockedUntil = user.getLoginLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            return;
        }

        Instant lastAttempt = user.getLastFailedLoginAt();
        boolean attemptWindowExpired = lastAttempt == null
                || !lastAttempt.isAfter(now.minus(LOCKOUT_DURATION));

        // Count only failures inside the rolling lockout window. Once a lock expires,
        // or the previous failures age out, a new window starts.
        if (lockedUntil != null || attemptWindowExpired
                || user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.setFailedLoginAttempts(0);
            user.setLoginLockedUntil(null);
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        user.setLastFailedLoginAt(now);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLoginLockedUntil(now.plus(LOCKOUT_DURATION));
        }
        userRepository.save(user);
    }
}
