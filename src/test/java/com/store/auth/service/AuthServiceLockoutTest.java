package com.store.auth.service;

import com.store.auth.dto.LoginRequest;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.common.exception.UnauthorizedException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLockoutTest {

    private static final String EMAIL = "race-lockout@example.com";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailOtpService emailOtpService;
    @Mock private AdminLoginGateService adminLoginGateService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private EntityManager entityManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                jwtService,
                authenticationManager,
                emailOtpService,
                adminLoginGateService,
                loginAttemptService);
        ReflectionTestUtils.setField(authService, "entityManager", entityManager);
    }

    @Test
    void refusesToMintSessionWhenAccountBecomesLockedDuringOtpVerification() {
        UUID challengeId = UUID.randomUUID();
        Role role = Role.builder().name("ROLE_USER").description("Standard customer").build();
        User user = User.builder()
                .firstName("Race")
                .lastName("OTP")
                .email(EMAIL)
                .password("encoded-password")
                .isEnabled(true)
                .emailVerified(true)
                .role(role)
                .build();
        EmailOtpChallenge challenge = EmailOtpChallenge.builder()
                .challengeId(challengeId)
                .user(user)
                .purpose(EmailOtpPurpose.LOGIN)
                .codeHash("0".repeat(64))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(emailOtpService.verifyChallenge(
                eq(challengeId), eq("123456"), any(EmailOtpPurpose[].class)))
                .thenReturn(challenge);
        doAnswer(invocation -> {
            user.setLoginLockedUntil(Instant.now().plusSeconds(900));
            user.setFailedLoginAttempts(5);
            return null;
        }).when(entityManager).refresh(eq(user), eq(LockModeType.PESSIMISTIC_WRITE));

        assertThrows(UnauthorizedException.class,
                () -> authService.verifyEmailCode(challengeId.toString(), "123456"));

        verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(jwtService);
    }

    @Test
    void refusesToIssueOtpWhenPasswordChangesDuringCredentialCheck() {
        Role role = Role.builder().name("ROLE_USER").description("Standard customer").build();
        User user = User.builder()
                .firstName("Race")
                .lastName("Password")
                .email(EMAIL)
                .password("new-encoded-password")
                .isEnabled(true)
                .emailVerified(true)
                .authVersion(1L)
                .role(role)
                .build();
        AccountUserDetails authenticatedPrincipal = new AccountUserDetails(
                EMAIL, "old-encoded-password", true, true, List.of(), 0L);

        when(loginAttemptService.isLocked(EMAIL)).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        authenticatedPrincipal, null, List.of()));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> authService.login(
                LoginRequest.builder().email(EMAIL).password("OldPassword123").build()));

        verify(jwtService, never()).generateToken(any(org.springframework.security.core.userdetails.UserDetails.class));
        verify(emailOtpService, never()).issueChallenge(any(User.class), any(EmailOtpPurpose.class));
        verify(emailOtpService, never()).issueChallenge(
                any(User.class), any(EmailOtpPurpose.class), anyLong());
    }

    @Test
    void refusesToIssueOtpOrSessionWhenAccountBecomesLockedDuringCredentialCheck() {
        Role role = Role.builder()
                .name("ROLE_USER")
                .description("Standard customer")
                .build();
        User user = User.builder()
                .firstName("Race")
                .lastName("Condition")
                .email(EMAIL)
                .password("encoded-password")
                .isEnabled(true)
                .emailVerified(true)
                .role(role)
                .build();

        when(loginAttemptService.isLocked(EMAIL)).thenReturn(false);
        AccountUserDetails authenticatedPrincipal = new AccountUserDetails(
                EMAIL, "encoded-password", true, true, List.of(), 0L);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        authenticatedPrincipal, null, List.of()));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        doAnswer(invocation -> {
            user.setLoginLockedUntil(Instant.now().plusSeconds(900));
            user.setFailedLoginAttempts(5);
            return null;
        }).when(entityManager).refresh(eq(user));

        assertThrows(UnauthorizedException.class, () -> authService.login(
                LoginRequest.builder()
                        .email(EMAIL)
                        .password("Password123")
                        .build()));

        verify(entityManager).refresh(user);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtService);
        verify(emailOtpService, never()).issueChallenge(any(User.class), any(EmailOtpPurpose.class));
        verify(emailOtpService, never()).issueChallenge(
                any(User.class), any(EmailOtpPurpose.class), anyLong());
    }
}
