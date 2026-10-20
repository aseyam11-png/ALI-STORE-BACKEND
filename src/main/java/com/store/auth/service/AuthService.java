package com.store.auth.service;

import com.store.auth.dto.AuthResponse;
import com.store.auth.dto.EmailOtpChallengeResponse;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.exception.UnauthorizedException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailOtpService emailOtpService;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public AuthResponse register(com.store.auth.dto.RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        // Match the expensive password-hashing work for existing and new addresses so
        // duplicate-registration responses are less distinguishable by request timing.
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            if (emailOtpService.isVerificationEnabled()) {
                // Use a decoy challenge to keep status, message, and response shape the
                // same for existing and new addresses without changing an existing account.
                return pendingVerificationResponse(
                        email,
                        "ROLE_USER",
                        emailOtpService.decoyChallenge(email, EmailOtpPurpose.EMAIL_VERIFICATION));
            }
            throw new ConflictException("Email already in use");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Default role not found"));

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .password(encodedPassword)
                .phoneNumber(request.getPhoneNumber() == null ? null : request.getPhoneNumber().trim())
                .role(userRole)
                .isEnabled(true)
                .emailVerified(!emailOtpService.isVerificationEnabled())
                .build();

        userRepository.save(user);

        if (!emailOtpService.isVerificationEnabled()) {
            return authenticatedResponse(user);
        }

        EmailOtpChallengeResponse challenge =
                emailOtpService.issueChallenge(user, EmailOtpPurpose.EMAIL_VERIFICATION);

        return pendingVerificationResponse(user, challenge);
    }

    @Transactional
    public AuthResponse login(com.store.auth.dto.LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (loginAttemptService.isLocked(email)) {
            throw new UnauthorizedException("Invalid email or password");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException ex) {
            // REQUIRES_NEW commits the failed attempt independently of this method's
            // transaction, which is rolled back when the sanitized 401 is raised.
            loginAttemptService.recordFailure(email);
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // A successful primary credential check clears the account-scoped lockout state.
        if (user.getFailedLoginAttempts() != 0
                || user.getLoginLockedUntil() != null
                || user.getLastFailedLoginAt() != null) {
            user.setFailedLoginAttempts(0);
            user.setLoginLockedUntil(null);
            user.setLastFailedLoginAt(null);
            userRepository.save(user);
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            if (!emailOtpService.isVerificationEnabled()) {
                user.setEmailVerified(true);
                userRepository.save(user);
                return authenticatedResponse(user);
            }

            EmailOtpChallengeResponse challenge =
                    emailOtpService.issueChallenge(user, EmailOtpPurpose.EMAIL_VERIFICATION);
            return pendingVerificationResponse(user, challenge);
        }

        if (emailOtpService.isLoginOtpEnabled()) {
            EmailOtpChallengeResponse challenge =
                    emailOtpService.issueChallenge(user, EmailOtpPurpose.LOGIN);
            return pendingVerificationResponse(user, challenge);
        }

        return authenticatedResponse(user);
    }

    /*
     * Keep the OTP consumption and email-verified state change atomic, but commit
     * expected BusinessException outcomes so wrong-code counters are never rolled back.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse verifyEmailCode(String challengeId, String code) {
        UUID parsedChallengeId = parseChallengeId(challengeId);
        EmailOtpChallenge challenge = emailOtpService.verifyChallenge(
                parsedChallengeId,
                code,
                EmailOtpPurpose.EMAIL_VERIFICATION,
                EmailOtpPurpose.LOGIN);
        User user = challenge.getUser();

        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new BusinessException("User account is disabled");
        }

        // A challenge may have been issued before concurrent failed password attempts
        // locked the account. Do not let completing that pending challenge mint a new JWT
        // while account lockout is active. This runtime exception rolls back OTP consumption.
        if (loginAttemptService.isLocked(user.getEmail())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (challenge.getPurpose() == EmailOtpPurpose.EMAIL_VERIFICATION
                && !Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        return authenticatedResponse(user);
    }

    public EmailOtpChallengeResponse resendEmailCode(String challengeId) {
        UUID parsedChallengeId = parseChallengeId(challengeId);
        return emailOtpService.resend(parsedChallengeId);
    }

    /**
     * Always returns normally and lets the controller send the same 202 response.
     * The response body must not disclose whether the address is registered,
     * disabled, or currently inside the resend cooldown.
     */
    public EmailOtpChallengeResponse requestPasswordReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            return emailOtpService.decoyChallenge("user@example.com", EmailOtpPurpose.PASSWORD_RESET);
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsEnabled())) {
            return emailOtpService.decoyChallenge(email, EmailOtpPurpose.PASSWORD_RESET);
        }

        try {
            // Return the challenge ID to the browser regardless of whether the email exists.
            // The code itself is delivered only to the account's address.
            EmailOtpChallengeResponse response =
                    emailOtpService.issueChallenge(user, EmailOtpPurpose.PASSWORD_RESET);
            response.setExpiresInSeconds(null);
            return response;
        } catch (RuntimeException ex) {
            // Keep the response shape the same on provider failure. The decoy ID cannot
            // validate and no account-existence signal is returned in the response body.
            log.warn("Password reset could not be issued for an eligible account", ex);
            return emailOtpService.decoyChallenge(email, EmailOtpPurpose.PASSWORD_RESET);
        }
    }

    /*
     * Keep successful OTP consumption and the password/account-version update in one
     * transaction. Wrong-code BusinessExceptions still commit their attempt counter.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(String challengeId, String code, String newPassword) {
        UUID parsedChallengeId = parseChallengeId(challengeId, EmailOtpPurpose.PASSWORD_RESET);
        EmailOtpChallenge challenge = emailOtpService.verifyChallenge(
                parsedChallengeId, code, EmailOtpPurpose.PASSWORD_RESET);
        User user = challenge.getUser();

        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new BusinessException("Invalid or expired password reset request");
        }

        emailOtpService.revokePendingAuthenticationChallenges(user.getId());
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        user.setLastFailedLoginAt(null);
        user.setAuthVersion(user.getAuthVersion() + 1L);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));

        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new UnauthorizedException("Authentication is required");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("New password must be different from the current password");
        }

        emailOtpService.revokePendingAuthenticationChallenges(user.getId());
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        user.setLastFailedLoginAt(null);
        user.setAuthVersion(user.getAuthVersion() + 1L);
        userRepository.save(user);
    }

    private UUID parseChallengeId(String challengeId) {
        return parseChallengeId(challengeId, null);
    }

    private UUID parseChallengeId(String challengeId, EmailOtpPurpose purpose) {
        try {
            return UUID.fromString(challengeId);
        } catch (RuntimeException ex) {
            throw new BusinessException(purpose == EmailOtpPurpose.PASSWORD_RESET
                    ? "Invalid or expired password reset request"
                    : "Invalid or expired verification challenge");
        }
    }

    private AuthResponse pendingVerificationResponse(
            User user,
            EmailOtpChallengeResponse challenge) {
        return pendingVerificationResponse(user.getEmail(), user.getRole().getName(), challenge);
    }

    private AuthResponse pendingVerificationResponse(
            String email,
            String role,
            EmailOtpChallengeResponse challenge) {
        return AuthResponse.builder()
                .email(email)
                .role(role)
                .requiresEmailVerification(true)
                .challengeId(challenge.getChallengeId())
                .maskedEmail(challenge.getMaskedEmail())
                .expiresInSeconds(challenge.getExpiresInSeconds())
                .purpose(challenge.getPurpose())
                .build();
    }

    private AuthResponse authenticatedResponse(User user) {
        var userDetails = new AccountUserDetails(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getIsEnabled()),
                true,
                Collections.singletonList(
                        new SimpleGrantedAuthority(user.getRole().getName())),
                user.getAuthVersion());

        String jwtToken = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .role(user.getRole().getName())
                .requiresEmailVerification(false)
                .build();
    }
}
