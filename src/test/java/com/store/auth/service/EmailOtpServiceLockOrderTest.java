package com.store.auth.service;

import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.auth.repository.EmailOtpChallengeRepository;
import com.store.common.exception.BusinessException;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceLockOrderTest {

    @Mock private EmailOtpChallengeRepository challengeRepository;
    @Mock private EmailOtpSender emailOtpSender;
    @Mock private Environment environment;
    @Mock private UserRepository userRepository;

    private EmailOtpService service;
    private User user;
    private EmailOtpChallenge challenge;
    private UUID challengeId;

    @BeforeEach
    void setUp() {
        service = new EmailOtpService(
                challengeRepository, emailOtpSender, environment, userRepository);
        ReflectionTestUtils.setField(service, "hashSecret", new byte[32]);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        when(environment.getProperty("app.security.admin-gate.enabled", "false")).thenReturn("false");

        user = User.builder()
                .firstName("Store")
                .lastName("Admin")
                .email("admin@example.invalid")
                .password("encoded")
                .isEnabled(true)
                .emailVerified(true)
                .build();
        ReflectionTestUtils.setField(user, "id", 42L);

        challengeId = UUID.randomUUID();
        challenge = EmailOtpChallenge.builder()
                .challengeId(challengeId)
                .user(user)
                .purpose(EmailOtpPurpose.LOGIN)
                .authVersion(user.getAuthVersion())
                .codeHash("0".repeat(64))
                .expiresAt(Instant.now().plusSeconds(600))
                .attemptCount(0)
                .build();

        when(challengeRepository.findByChallengeId(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(challengeRepository.findForVerification(challengeId)).thenReturn(Optional.of(challenge));
    }

    @Test
    void verificationLocksUserBeforeChallengeAndCountsWrongCode() {
        assertThrows(BusinessException.class,
                () -> service.verifyChallenge(challengeId, "123456", EmailOtpPurpose.LOGIN));

        InOrder lockOrder = inOrder(challengeRepository, userRepository);
        lockOrder.verify(challengeRepository).findByChallengeId(challengeId);
        lockOrder.verify(userRepository).findByIdForUpdate(42L);
        lockOrder.verify(challengeRepository).findForVerification(challengeId);

        assertEquals(1, challenge.getAttemptCount());
        verify(challengeRepository).save(challenge);
    }

    @Test
    void disabledAccountCannotConsumeAnExistingOtpChallenge() {
        user.setIsEnabled(false);

        assertThrows(BusinessException.class,
                () -> service.verifyChallenge(challengeId, "123456", EmailOtpPurpose.LOGIN));

        assertNotNull(challenge.getRevokedAt());
        assertEquals(0, challenge.getAttemptCount());
        verify(challengeRepository).save(challenge);
    }
}
