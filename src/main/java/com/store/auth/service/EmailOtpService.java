package com.store.auth.service;

import com.store.auth.dto.EmailOtpChallengeResponse;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.auth.repository.EmailOtpChallengeRepository;
import com.store.common.exception.BusinessException;
import com.store.user.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private static final int CODE_DIGITS = 6;
    private static final int MIN_KEY_BYTES = 32;
    private static final int DEFAULT_EXPIRATION_SECONDS = 10 * 60;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RESEND_COOLDOWN_SECONDS = 60;

    private final EmailOtpChallengeRepository challengeRepository;
    private final EmailOtpSender emailOtpSender;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] hashSecret;
    private boolean verificationEnabled;
    private boolean loginOtpEnabled;
    private int expirationSeconds;
    private int maxAttempts;
    private int resendCooldownSeconds;

    @PostConstruct
    void initialize() {
        verificationEnabled = Boolean.parseBoolean(
                environment.getProperty("app.auth.email-verification-enabled", "true"));
        loginOtpEnabled = Boolean.parseBoolean(
                environment.getProperty("app.auth.login-email-otp-enabled", "true"));
        expirationSeconds = positiveInt(
                environment.getProperty(
                        "app.auth.email-code-expiration-seconds",
                        String.valueOf(DEFAULT_EXPIRATION_SECONDS)),
                DEFAULT_EXPIRATION_SECONDS);
        maxAttempts = positiveInt(
                environment.getProperty(
                        "app.auth.email-code-max-attempts",
                        String.valueOf(DEFAULT_MAX_ATTEMPTS)),
                DEFAULT_MAX_ATTEMPTS);
        resendCooldownSeconds = positiveInt(
                environment.getProperty(
                        "app.auth.email-code-resend-cooldown-seconds",
                        String.valueOf(DEFAULT_RESEND_COOLDOWN_SECONDS)),
                DEFAULT_RESEND_COOLDOWN_SECONDS);

        if (isProd() && !verificationEnabled) {
            throw new IllegalStateException(
                    "AUTH_EMAIL_VERIFICATION_ENABLED must be true in production to protect registration");
        }

        String configuredSecret = environment.getProperty("AUTH_OTP_HASH_SECRET", "");
        if (!hasText(configuredSecret) && isProd()) {
            throw new IllegalStateException(
                    "AUTH_OTP_HASH_SECRET must be configured separately in production");
        }
        if (!hasText(configuredSecret)) {
            configuredSecret = environment.getProperty("JWT_SECRET", "");
        }

        try {
            hashSecret = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AUTH_OTP_HASH_SECRET must be valid Base64", ex);
        }

        if (hashSecret.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("AUTH_OTP_HASH_SECRET must decode to at least 32 bytes");
        }

        if (isProd() && (verificationEnabled || loginOtpEnabled)) {
            requireText("MAIL_HOST", environment.getProperty("spring.mail.host", ""));
            requireText("MAIL_FROM", environment.getProperty("app.mail.from", ""));
        }
    }

    public boolean isVerificationEnabled() {
        return verificationEnabled;
    }

    public boolean isLoginOtpEnabled() {
        return loginOtpEnabled;
    }

    /**
     * Creates the same public response shape used for a real registration challenge,
     * but does not persist a challenge or send a message. This prevents duplicate
     * registrations from returning a visibly different status or error.
     */
    public EmailOtpChallengeResponse decoyChallenge(String email, EmailOtpPurpose purpose) {
        return EmailOtpChallengeResponse.builder()
                .requiresEmailVerification(true)
                .challengeId(UUID.randomUUID().toString())
                .maskedEmail(maskEmail(email))
                .expiresInSeconds(purpose == EmailOtpPurpose.PASSWORD_RESET ? null : (long) expirationSeconds)
                .purpose(purpose)
                .build();
    }

    @Transactional
    public EmailOtpChallengeResponse issueChallenge(User user, EmailOtpPurpose purpose) {
        Instant now = Instant.now();

        challengeRepository.findTopByUserIdAndPurposeAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                        user.getId(), purpose)
                .filter(existing -> existing.getCreatedAt() != null
                        && now.isBefore(existing.getCreatedAt().toInstant(java.time.ZoneOffset.UTC)
                        .plusSeconds(resendCooldownSeconds)))
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "A verification code was already sent. Please wait before requesting another code.");
                });

        challengeRepository.revokeActiveChallenges(user.getId(), purpose, now);

        String code = generateCode();
        UUID challengeId = UUID.randomUUID();

        EmailOtpChallenge challenge = EmailOtpChallenge.builder()
                .challengeId(challengeId)
                .user(user)
                .purpose(purpose)
                .codeHash(hash(challengeId, code))
                .expiresAt(now.plusSeconds(expirationSeconds))
                .attemptCount(0)
                .build();

        challengeRepository.save(challenge);

        try {
            emailOtpSender.send(
                    user.getEmail(),
                    code,
                    purpose,
                    Duration.ofSeconds(expirationSeconds));
        } catch (RuntimeException ex) {
            challenge.setRevokedAt(Instant.now());
            challengeRepository.save(challenge);
            throw ex;
        }

        return EmailOtpChallengeResponse.builder()
                .requiresEmailVerification(true)
                .challengeId(challengeId.toString())
                .maskedEmail(maskEmail(user.getEmail()))
                .expiresInSeconds(expirationSeconds)
                .purpose(purpose)
                .build();
    }

    /**
     * Used by the ordinary email verification/login OTP endpoint. The transaction
     * deliberately commits BusinessException outcomes because a failed code updates
     * attempt_count (and can revoke the challenge) immediately before throwing.
     * There must be no surrounding AuthService transaction that can roll this back.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public EmailOtpChallenge verifyChallenge(UUID challengeId, String code) {
        return verifyChallengeInternal(
                challengeId, code, EmailOtpPurpose.EMAIL_VERIFICATION, EmailOtpPurpose.LOGIN);
    }

    /**
     * Verifies a code for a specific purpose, so e.g. a PASSWORD_RESET code cannot
     * be consumed by the email-verification endpoint to create a login session.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public EmailOtpChallenge verifyChallenge(
            UUID challengeId, String code, EmailOtpPurpose... allowedPurposes) {
        return verifyChallengeInternal(challengeId, code, allowedPurposes);
    }

    private EmailOtpChallenge verifyChallengeInternal(
            UUID challengeId, String code, EmailOtpPurpose... allowedPurposes) {
        boolean passwordResetFlow = allowedPurposes != null
                && Arrays.stream(allowedPurposes).anyMatch(purpose -> purpose == EmailOtpPurpose.PASSWORD_RESET);
        if (code == null || !code.matches("\\d{6}")) {
            throw invalidChallengeException(passwordResetFlow);
        }

        EmailOtpChallenge challenge = challengeRepository.findForVerification(challengeId)
                .orElseThrow(() -> invalidChallengeException(passwordResetFlow));

        Instant now = Instant.now();
        if (challenge.getRevokedAt() != null
                || challenge.getConsumedAt() != null
                || !now.isBefore(challenge.getExpiresAt())) {
            throw invalidChallengeException(passwordResetFlow
                    || challenge.getPurpose() == EmailOtpPurpose.PASSWORD_RESET);
        }

        if (allowedPurposes != null && allowedPurposes.length > 0
                && Arrays.stream(allowedPurposes).noneMatch(purpose -> purpose == challenge.getPurpose())) {
            throw invalidChallengeException(passwordResetFlow);
        }

        if (challenge.getAttemptCount() >= maxAttempts) {
            challenge.setRevokedAt(now);
            challengeRepository.save(challenge);
            if (challenge.getPurpose() == EmailOtpPurpose.PASSWORD_RESET) {
                throw invalidChallengeException(true);
            }
            throw new BusinessException("Too many invalid verification attempts");
        }

        boolean valid = MessageDigest.isEqual(
                decodeHex(challenge.getCodeHash()),
                decodeHex(hash(challengeId, code)));

        if (!valid) {
            int attempts = challenge.getAttemptCount() + 1;
            challenge.setAttemptCount(attempts);
            if (attempts >= maxAttempts) {
                challenge.setRevokedAt(now);
            }
            challengeRepository.save(challenge);
            throw invalidChallengeException(challenge.getPurpose() == EmailOtpPurpose.PASSWORD_RESET);
        }

        challenge.setConsumedAt(now);
        challengeRepository.save(challenge);
        return challenge;
    }

    @Transactional
    public EmailOtpChallengeResponse resend(UUID challengeId) {
        EmailOtpChallenge current = challengeRepository.findForVerification(challengeId)
                .orElseThrow(() -> new BusinessException("Invalid verification challenge"));

        Instant now = Instant.now();
        if (current.getConsumedAt() != null) {
            throw new BusinessException("Verification challenge has already been completed");
        }

        if (current.getRevokedAt() != null || !now.isBefore(current.getExpiresAt())) {
            throw new BusinessException("Invalid or expired verification challenge");
        }

        if (current.getCreatedAt() != null
                && now.isBefore(current.getCreatedAt().toInstant(java.time.ZoneOffset.UTC)
                .plusSeconds(resendCooldownSeconds))) {
            throw new BusinessException(
                    "A verification code was already sent. Please wait before requesting another code.");
        }

        return issueChallenge(current.getUser(), current.getPurpose());
    }

    private BusinessException invalidChallengeException(boolean passwordResetFlow) {
        return new BusinessException(passwordResetFlow
                ? "Invalid or expired password reset request"
                : "Invalid or expired verification challenge");
    }

    private String generateCode() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", value);
    }

    private String hash(UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(
                    (challengeId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash verification code", ex);
        }
    }

    private byte[] decodeHex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String maskEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? normalized.substring(at) : "");
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at);
        return local.charAt(0) + "***" + domain;
    }

    private void requireText(String name, String value) {
        if (!hasText(value)) throw new IllegalStateException(name + " must be configured in production");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isProd() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }
}
