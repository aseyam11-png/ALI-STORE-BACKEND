package com.store.auth.service;

import com.store.auth.dto.AuthResponse;
import com.store.auth.dto.EmailOtpChallengeResponse;
import com.store.auth.dto.LoginRequest;
import com.store.auth.dto.RegisterRequest;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailOtpService emailOtpService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already in use");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Default role not found"));

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
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
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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

    @Transactional
    public AuthResponse verifyEmailCode(String challengeId, String code) {
        final java.util.UUID parsedChallengeId;
        try {
            parsedChallengeId = java.util.UUID.fromString(challengeId);
        } catch (IllegalArgumentException ex) {
            throw new com.store.common.exception.BusinessException(
                    "Invalid verification challenge");
        }

        EmailOtpChallenge challenge = emailOtpService.verifyChallenge(parsedChallengeId, code);
        User user = challenge.getUser();

        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new com.store.common.exception.BusinessException("User account is disabled");
        }

        if (challenge.getPurpose() == EmailOtpPurpose.EMAIL_VERIFICATION
                && !Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        return authenticatedResponse(user);
    }

    @Transactional
    public EmailOtpChallengeResponse resendEmailCode(String challengeId) {
        final java.util.UUID parsedChallengeId;
        try {
            parsedChallengeId = java.util.UUID.fromString(challengeId);
        } catch (IllegalArgumentException ex) {
            throw new com.store.common.exception.BusinessException(
                    "Invalid verification challenge");
        }
        return emailOtpService.resend(parsedChallengeId);
    }

    private AuthResponse pendingVerificationResponse(
            User user,
            EmailOtpChallengeResponse challenge) {
        return AuthResponse.builder()
                .email(user.getEmail())
                .role(user.getRole().getName())
                .requiresEmailVerification(true)
                .challengeId(challenge.getChallengeId())
                .maskedEmail(challenge.getMaskedEmail())
                .expiresInSeconds(challenge.getExpiresInSeconds())
                .purpose(challenge.getPurpose())
                .build();
    }

    private AuthResponse authenticatedResponse(User user) {
        var userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getIsEnabled()),
                true,
                true,
                true,
                Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                user.getRole().getName()))
        );

        String jwtToken = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .role(user.getRole().getName())
                .requiresEmailVerification(false)
                .build();
    }
}
