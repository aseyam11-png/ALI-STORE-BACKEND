package com.store.auth.service;

import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Persistent login-attempt state, isolated from rollback of failed login requests. */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    private final UserRepository userRepository;

    @Value("${app.auth.login-max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.auth.login-lockout-minutes:15}")
    private long lockoutMinutes;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        LocalDateTime now = LocalDateTime.now();
        int maxAttempts = Math.max(3, Math.min(20, maxFailedAttempts));
        long lockMinutes = Math.max(1, Math.min(1440, lockoutMinutes));
        userRepository.recordFailedLogin(email, maxAttempts, now, now.plusMinutes(lockMinutes));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email) {
        userRepository.resetFailedLogin(email);
    }
}
