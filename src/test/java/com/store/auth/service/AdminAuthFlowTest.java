package com.store.auth.service;

import com.store.auth.dto.AuthResponse;
import com.store.auth.dto.EmailOtpChallengeResponse;
import com.store.auth.dto.LoginRequest;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.common.exception.BusinessException;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthFlowTest {

    private static final String EMAIL = "admin@example.invalid";
    private static final String PASSWORD = "PrimaryPassword123";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailOtpService emailOtpService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AdminLoginGateService adminLoginGateService;
    @Mock private EntityManager entityManager;

    private AuthService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                jwtService,
                authenticationManager,
                emailOtpService,
                loginAttemptService,
                adminLoginGateService);
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "entityManager", entityManager);

        Role adminRole = Role.builder()
                .name("ROLE_ADMIN")
                .description("Administrator")
                .build();

        admin = User.builder()
                .firstName("Store")
                .lastName("Admin")
                .email(EMAIL)
                .password("encoded-password")
                .isEnabled(true)
                .emailVerified(true)
                .role(adminRole)
                .build();
    }

    private void authenticateAdmin() {
        when(loginAttemptService.isLocked(EMAIL)).thenReturn(false);
        AccountUserDetails principal = new AccountUserDetails(
                EMAIL,
                "encoded-password",
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                admin.getAuthVersion());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(admin));
        doAnswer(invocation -> null).when(entityManager).refresh(admin);
    }

    @Test
    void adminPasswordAndGateStepCreatesPurposeBoundOtpWithoutIssuingSession() {
        authenticateAdmin();
        when(adminLoginGateService.verifyIfAdmin(
                eq(admin), any(LoginRequest.class), anyString())).thenReturn(true);

        UUID challengeId = UUID.randomUUID();
        EmailOtpChallengeResponse challenge = EmailOtpChallengeResponse.builder()
                .requiresEmailVerification(true)
                .challengeId(challengeId.toString())
                .maskedEmail("a***@example.invalid")
                .expiresInSeconds(600L)
                .purpose(EmailOtpPurpose.ADMIN_LOGIN)
                .build();
        when(emailOtpService.issueChallenge(
                eq(admin), eq(EmailOtpPurpose.ADMIN_LOGIN), eq(admin.getAuthVersion()), eq(true)))
                .thenReturn(challenge);

        AuthResponse response = service.adminLogin(
                LoginRequest.builder().email(EMAIL).password(PASSWORD)
                        .adminGatePassword("second-password-16chars")
                        .captchaToken("verified-captcha-token").build(),
                "198.51.100.20");

        assertTrue(response.isRequiresEmailVerification());
        assertEquals(challengeId.toString(), response.getChallengeId());
        assertEquals(EmailOtpPurpose.ADMIN_LOGIN, response.getPurpose());
        assertNull(response.getToken(), "The first factors must not create an authenticated session");
        verify(adminLoginGateService).verifyIfAdmin(
                eq(admin), any(LoginRequest.class), eq("198.51.100.20"));
        verify(emailOtpService).issueChallenge(
                eq(admin), eq(EmailOtpPurpose.ADMIN_LOGIN), eq(admin.getAuthVersion()), eq(true));
        verifyNoInteractions(jwtService);
    }

    @Test
    void customerLoginRejectsAdministratorEvenWhenPrimaryPasswordIsCorrect() {
        authenticateAdmin();

        assertThrows(UnauthorizedException.class, () -> service.login(
                LoginRequest.builder().email(EMAIL).password(PASSWORD).build(),
                "198.51.100.20"));

        verifyNoInteractions(adminLoginGateService, emailOtpService, jwtService);
    }

    @Test
    void adminOtpMintsSessionOnlyForAdminPurposeAndCarriesSignedGateProof() {
        UUID challengeId = UUID.randomUUID();
        EmailOtpChallenge challenge = EmailOtpChallenge.builder()
                .challengeId(challengeId)
                .user(admin)
                .purpose(EmailOtpPurpose.ADMIN_LOGIN)
                .authVersion(admin.getAuthVersion())
                .codeHash("0".repeat(64))
                .expiresAt(Instant.now().plusSeconds(600))
                .adminGateVerified(true)
                .build();
        when(emailOtpService.verifyChallenge(
                eq(challengeId), eq("482915"), eq(EmailOtpPurpose.ADMIN_LOGIN)))
                .thenReturn(challenge);
        doAnswer(invocation -> null).when(entityManager)
                .refresh(admin, LockModeType.PESSIMISTIC_WRITE);
        when(jwtService.generateToken(anyMap(), any(AccountUserDetails.class)))
                .thenReturn("admin-session-token");

        AuthResponse response = service.verifyAdminLoginCode(challengeId.toString(), "482915");

        assertFalse(response.isRequiresEmailVerification());
        assertEquals("ROLE_ADMIN", response.getRole());
        assertEquals(EmailOtpPurpose.ADMIN_LOGIN, response.getPurpose());
        verify(emailOtpService).verifyChallenge(
                eq(challengeId), eq("482915"), eq(EmailOtpPurpose.ADMIN_LOGIN));
        verify(jwtService).generateToken(
                argThat(claims -> Boolean.TRUE.equals(claims.get("admin_gate_verified"))),
                any(AccountUserDetails.class));
    }

    @Test
    void genericCustomerOtpEndpointCannotConsumeAdministratorChallenge() {
        UUID challengeId = UUID.randomUUID();
        EmailOtpChallenge challenge = EmailOtpChallenge.builder()
                .challengeId(challengeId)
                .user(admin)
                .purpose(EmailOtpPurpose.ADMIN_LOGIN)
                .codeHash("0".repeat(64))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(emailOtpService.verifyChallenge(
                eq(challengeId), eq("482915"), any(EmailOtpPurpose[].class)))
                .thenReturn(challenge);

        assertThrows(BusinessException.class,
                () -> service.verifyEmailCode(challengeId.toString(), "482915"));
        verifyNoInteractions(jwtService);
    }

    @Test
    void adminOtpEndpointCannotAuthenticateCustomerChallenge() {
        User customer = User.builder()
                .firstName("Regular")
                .lastName("Customer")
                .email("customer@example.invalid")
                .password("encoded-password")
                .isEnabled(true)
                .emailVerified(true)
                .role(Role.builder().name("ROLE_USER").build())
                .build();
        UUID challengeId = UUID.randomUUID();
        EmailOtpChallenge challenge = EmailOtpChallenge.builder()
                .challengeId(challengeId)
                .user(customer)
                .purpose(EmailOtpPurpose.ADMIN_LOGIN)
                .codeHash("0".repeat(64))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(emailOtpService.verifyChallenge(
                eq(challengeId), eq("482915"), eq(EmailOtpPurpose.ADMIN_LOGIN)))
                .thenReturn(challenge);
        doAnswer(invocation -> null).when(entityManager)
                .refresh(customer, LockModeType.PESSIMISTIC_WRITE);

        assertThrows(BusinessException.class,
                () -> service.verifyAdminLoginCode(challengeId.toString(), "482915"));
        verifyNoInteractions(jwtService);
    }
}
