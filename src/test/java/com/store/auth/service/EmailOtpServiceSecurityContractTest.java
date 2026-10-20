package com.store.auth.service;

import com.store.auth.dto.EmailOtpChallengeResponse;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.auth.repository.EmailOtpChallengeRepository;
import com.store.common.exception.BusinessException;
import com.store.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceSecurityContractTest {

    @Mock private EmailOtpChallengeRepository challengeRepository;
    @Mock private EmailOtpSender emailOtpSender;

    private EmailOtpService service;
    private User user;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.auth.email-verification-enabled", "true")
                .withProperty("app.auth.login-email-otp-enabled", "true")
                .withProperty("app.auth.email-code-expiration-seconds", "600")
                .withProperty("app.auth.email-code-max-attempts", "5")
                .withProperty("app.auth.email-code-resend-cooldown-seconds", "60")
                .withProperty(
                        "AUTH_OTP_HASH_SECRET",
                        Base64.getEncoder().encodeToString(
                                "otp-test-secret-with-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8)));

        service = new EmailOtpService(challengeRepository, emailOtpSender, environment);
        service.initialize();

        user = User.builder()
                .firstName("OTP")
                .lastName("Test")
                .email("user@example.com")
                .password("ignored")
                .isEnabled(true)
                .emailVerified(true)
                .build();
        user.setId(42L);
    }

    @Test
    void decoyChallengeHandlesNullEmailWithoutThrowing() {
        EmailOtpChallengeResponse response =
                service.decoyChallenge(null, EmailOtpPurpose.PASSWORD_RESET);

        assertNotNull(response.getChallengeId());
        assertEquals("***", response.getMaskedEmail());
        assertNull(response.getExpiresInSeconds());
        assertEquals(EmailOtpPurpose.PASSWORD_RESET, response.getPurpose());
    }

    @Test
    void issueChallenge_persistsOnlyHmacDerivedCodeAndsendsSixDigits() {
        when(challengeRepository
                .findTopByUserIdAndPurposeAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                        eq(42L), eq(EmailOtpPurpose.LOGIN)))
                .thenReturn(Optional.empty());
        when(challengeRepository.revokeActiveChallenges(
                eq(42L), eq(EmailOtpPurpose.LOGIN), any(Instant.class)))
                .thenReturn(0);
        when(challengeRepository.save(any(EmailOtpChallenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        EmailOtpChallengeResponse response =
                service.issueChallenge(user, EmailOtpPurpose.LOGIN);

        verify(emailOtpSender).send(
                eq("user@example.com"),
                codeCaptor.capture(),
                eq(EmailOtpPurpose.LOGIN),
                eq(java.time.Duration.ofSeconds(600)));

        String code = codeCaptor.getValue();
        EmailOtpChallenge persisted = capturePersistedChallenge();

        assertTrue(code.matches("\\d{6}"));
        assertEquals(64, persisted.getCodeHash().length());
        assertNotEquals(code, persisted.getCodeHash());
        assertEquals(response.getChallengeId(), persisted.getChallengeId().toString());
        assertEquals("u***@example.com", response.getMaskedEmail());
        assertEquals(600, response.getExpiresInSeconds());
    }

    @Test
    void verifyChallenge_consumesValidCode_andRejectsFurtherUse() {
        UUID challengeId = issueChallengeForVerification();
        EmailOtpChallenge challenge = capturePersistedChallenge();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(anyString(), codeCaptor.capture(), any(), any());

        when(challengeRepository.findForVerification(challengeId))
                .thenReturn(Optional.of(challenge));

        EmailOtpChallenge verified =
                service.verifyChallenge(challengeId, codeCaptor.getValue());

        assertEquals(challenge, verified);
        assertNotNull(challenge.getConsumedAt());

        assertThrows(
                BusinessException.class,
                () -> service.verifyChallenge(challengeId, codeCaptor.getValue()));
    }

    @Test
    void verifyChallenge_revokesChallengeAfterMaximumInvalidAttempts() {
        UUID challengeId = issueChallengeForVerification();
        EmailOtpChallenge challenge = capturePersistedChallenge();

        when(challengeRepository.findForVerification(challengeId))
                .thenReturn(Optional.of(challenge));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(anyString(), codeCaptor.capture(), any(), any());
        String issuedCode = codeCaptor.getValue();
        String wrongCode = "000000".equals(issuedCode) ? "000001" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(
                    BusinessException.class,
                    () -> service.verifyChallenge(challengeId, wrongCode));
        }

        assertEquals(5, challenge.getAttemptCount());
        assertNotNull(challenge.getRevokedAt());

        assertThrows(
                BusinessException.class,
                () -> service.verifyChallenge(challengeId, wrongCode));
    }

    @Test
    void verifyChallenge_rejectsExpiredChallenge_evenWhenCodeFormatIsValid() {
        UUID challengeId = issueChallengeForVerification();
        EmailOtpChallenge challenge = capturePersistedChallenge();
        challenge.setExpiresAt(Instant.now().minusSeconds(1));

        when(challengeRepository.findForVerification(challengeId))
                .thenReturn(Optional.of(challenge));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(anyString(), codeCaptor.capture(), any(), any());
        String issuedCode = codeCaptor.getValue();
        String wrongCode = "123456".equals(issuedCode) ? "123457" : "123456";

        assertThrows(
                BusinessException.class,
                () -> service.verifyChallenge(challengeId, wrongCode));
    }

    @Test
    void verifyChallenge_rejectsWrongPurposeWithoutCreatingSession() {
        UUID challengeId = issueChallengeForVerification();
        EmailOtpChallenge challenge = capturePersistedChallenge();
        when(challengeRepository.findForVerification(challengeId))
                .thenReturn(Optional.of(challenge));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(anyString(), codeCaptor.capture(), any(), any());
        assertThrows(BusinessException.class, () -> service.verifyChallenge(
                challengeId, codeCaptor.getValue(), EmailOtpPurpose.PASSWORD_RESET));
        assertNull(challenge.getConsumedAt());
    }

    @Test
    void failedCodeCounterUsesNoRollbackAndAuthServiceHasNoOuterVerificationTransaction() throws Exception {
        Transactional annotation = EmailOtpService.class
                .getMethod("verifyChallenge", UUID.class, String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertTrue(Arrays.asList(annotation.noRollbackFor()).contains(BusinessException.class));

        Transactional authAnnotation = AuthService.class
                .getMethod("verifyEmailCode", String.class, String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(authAnnotation);
        assertTrue(Arrays.asList(authAnnotation.noRollbackFor()).contains(BusinessException.class));
    }

    @Test
    void resend_rejectsRequestsInsideCooldownWindow() {
        UUID challengeId = UUID.randomUUID();
        EmailOtpChallenge current = EmailOtpChallenge.builder()
                .challengeId(challengeId)
                .user(user)
                .purpose(EmailOtpPurpose.LOGIN)
                .codeHash("0".repeat(64))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        current.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

        when(challengeRepository.findForVerification(challengeId))
                .thenReturn(Optional.of(current));

        assertThrows(BusinessException.class, () -> service.resend(challengeId));
        verifyNoInteractions(emailOtpSender);
    }

    private UUID issueChallengeForVerification() {
        when(challengeRepository
                .findTopByUserIdAndPurposeAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                        eq(42L), any(EmailOtpPurpose.class)))
                .thenReturn(Optional.empty());
        when(challengeRepository.revokeActiveChallenges(
                eq(42L), any(EmailOtpPurpose.class), any(Instant.class)))
                .thenReturn(0);
        when(challengeRepository.save(any(EmailOtpChallenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmailOtpChallengeResponse response =
                service.issueChallenge(user, EmailOtpPurpose.LOGIN);
        return UUID.fromString(response.getChallengeId());
    }

    private EmailOtpChallenge capturePersistedChallenge() {
        ArgumentCaptor<EmailOtpChallenge> captor =
                ArgumentCaptor.forClass(EmailOtpChallenge.class);
        verify(challengeRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(0);
    }
}
