package com.store.auth.service;

import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        if (email == null || email.isBlank()) return;
        User user = userRepository.findByEmailIgnoreCaseForUpdate(email.trim()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsEnabled())) return;

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) return;
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAfterSuccessfulLogin(String email) {
        if (email == null || email.isBlank()) return;
        userRepository.findByEmailIgnoreCaseForUpdate(email.trim()).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
    }
}
