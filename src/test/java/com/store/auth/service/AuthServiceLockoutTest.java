package com.store.auth.service;

import com.store.auth.dto.LoginRequest;
import com.store.auth.service.EmailOtpService;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
                loginAttemptService);
        ReflectionTestUtils.setField(authService, "entityManager", entityManager);
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
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of()));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        doAnswer(invocation -> {
            user.setLoginLockedUntil(Instant.now().plusSeconds(900));
            user.setFailedLoginAttempts(5);
            return null;
        }).when(entityManager).refresh(eq(user), eq(LockModeType.PESSIMISTIC_WRITE));

        assertThrows(UnauthorizedException.class, () -> authService.login(
                LoginRequest.builder()
                        .email(EMAIL)
                        .password("Password123")
                        .build()));

        verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtService, emailOtpService);
    }
}
