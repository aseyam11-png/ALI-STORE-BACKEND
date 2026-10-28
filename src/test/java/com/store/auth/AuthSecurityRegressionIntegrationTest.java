package com.store.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.dto.EmailOtpChallengeResponse;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.auth.repository.EmailOtpChallengeRepository;
import com.store.auth.service.AccountUserDetails;
import com.store.auth.service.AdminLoginGateService;
import com.store.auth.service.AuthService;
import com.store.auth.service.EmailOtpSender;
import com.store.auth.service.EmailOtpService;
import com.store.auth.service.JwtService;
import com.store.common.exception.BusinessException;
import com.store.common.exception.UnauthorizedException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityRegressionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailOtpChallengeRepository challengeRepository;
    @Autowired private EmailOtpService emailOtpService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;

    @MockBean
    private EmailOtpSender emailOtpSender;

    @MockBean
    private AdminLoginGateService adminLoginGateService;

    @Test
    void administratorPasswordLoginChecksSecondaryPasswordAndTurnstileBeforeIssuingOtp() throws Exception {
        User admin = createUser(uniqueEmail("admin-gate"));
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("ROLE_ADMIN").description("Store administrator").build()));
        admin.setRole(adminRole);
        userRepository.saveAndFlush(admin);
        clearInvocations(adminLoginGateService, emailOtpSender);

        mockMvc.perform(csrfPost("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", admin.getEmail(),
                                "password", "Password123",
                                "adminGatePassword", "SeparateAdminPassword123!",
                                "turnstileToken", "turnstile-response-token"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.requiresEmailVerification").value(true))
                .andExpect(jsonPath("$.data.purpose").value("LOGIN"));

        verify(adminLoginGateService).verify(
                "SeparateAdminPassword123!", "turnstile-response-token");
    }

    @Test
    void passwordAndOtpLoginCreatesHttpOnlyCookieAndDoesNotExposeJwt() throws Exception {
        User user = createUser(uniqueEmail("successful-login"));
        user.setFailedLoginAttempts(2);
        user.setLastFailedLoginAt(Instant.now().minusSeconds(10));
        userRepository.saveAndFlush(user);
        clearInvocations(emailOtpSender);

        MvcResult loginResult = mockMvc.perform(csrfPost("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(),
                                "password", "Password123"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.requiresEmailVerification").value(true))
                .andExpect(jsonPath("$.data.purpose").value("LOGIN"))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String challengeId = loginJson.path("data").path("challengeId").asText();
        assertFalse(challengeId.isBlank());
        assertFalse(loginJson.path("data").has("token"),
                "The primary login response must not expose the JWT in JSON");

        User pending = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertEquals(2, pending.getFailedLoginAttempts(),
                "Password failure counters should clear only after all login factors complete");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(
                eq(user.getEmail()), codeCaptor.capture(), eq(EmailOtpPurpose.LOGIN), any());

        MvcResult verificationResult = mockMvc.perform(csrfPost("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeId", challengeId,
                                "code", codeCaptor.getValue()))))
                .andExpect(status().isOk())
                .andReturn();

        assertFalse(objectMapper.readTree(verificationResult.getResponse().getContentAsString())
                        .path("data").has("token"),
                "The verification response must not expose the JWT in JSON");

        String sessionCookieHeader = verificationResult.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("ali_store_session="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Completing login must issue the session cookie"));
        assertTrue(sessionCookieHeader.toLowerCase().contains("httponly"));
        assertTrue(sessionCookieHeader.contains("Path=/"));
        assertTrue(sessionCookieHeader.contains("SameSite=Strict"));

        User completedLogin = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertEquals(0, completedLogin.getFailedLoginAttempts());
        assertNull(completedLogin.getLoginLockedUntil());

        String cookieValue = sessionCookieHeader.substring(
                "ali_store_session=".length(), sessionCookieHeader.indexOf(';'));
        mockMvc.perform(get("/api/v1/auth/session")
                        .cookie(new Cookie("ali_store_session", cookieValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));
    }

    @Test
    void pendingLoginOtpCannotCreateSessionDuringLockoutAndCanBeRetriedAfterUnlock() {
        User user = createUser(uniqueEmail("locked-pending-otp"));
        clearInvocations(emailOtpSender);

        EmailOtpChallengeResponse issued =
                emailOtpService.issueChallenge(user, EmailOtpPurpose.LOGIN);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(
                eq(user.getEmail()), codeCaptor.capture(), eq(EmailOtpPurpose.LOGIN), any());

        user.setFailedLoginAttempts(5);
        user.setLastFailedLoginAt(Instant.now());
        user.setLoginLockedUntil(Instant.now().plusSeconds(900));
        userRepository.saveAndFlush(user);

        assertThrows(UnauthorizedException.class,
                () -> authService.verifyEmailCode(issued.getChallengeId(), codeCaptor.getValue()));

        UUID challengeId = UUID.fromString(issued.getChallengeId());
        EmailOtpChallenge rejected = challengeRepository.findByChallengeId(challengeId).orElseThrow();
        assertNull(rejected.getConsumedAt(),
                "Rejecting a locked account must not consume its valid OTP");

        User unlocked = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        unlocked.setFailedLoginAttempts(0);
        unlocked.setLastFailedLoginAt(null);
        unlocked.setLoginLockedUntil(null);
        userRepository.saveAndFlush(unlocked);

        var authenticated = authService.verifyEmailCode(issued.getChallengeId(), codeCaptor.getValue());
        assertNotNull(authenticated.getToken());
        assertNotNull(challengeRepository.findByChallengeId(challengeId).orElseThrow().getConsumedAt());
    }

    @Test
    void passwordResetInvalidatesExistingJwtAndPendingLoginOtp() throws Exception {
        User user = createUser(uniqueEmail("reset-invalidates-session"));
        String oldJwt = tokenFor(user);
        EmailOtpChallengeResponse pendingLogin =
                emailOtpService.issueChallenge(user, EmailOtpPurpose.LOGIN);
        clearInvocations(emailOtpSender);

        MvcResult forgotResult = mockMvc.perform(csrfPost("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", user.getEmail()))))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode forgotJson = objectMapper.readTree(forgotResult.getResponse().getContentAsString());
        String resetChallengeId = forgotJson.path("data").path("challengeId").asText();
        ArgumentCaptor<String> resetCode = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(
                eq(user.getEmail()), resetCode.capture(), eq(EmailOtpPurpose.PASSWORD_RESET), any());

        mockMvc.perform(csrfPost("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeId", resetChallengeId,
                                "code", resetCode.getValue(),
                                "newPassword", "NewPassword456"))))
                .andExpect(status().isOk());

        User updated = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPassword456", updated.getPassword()));
        assertEquals(1L, updated.getAuthVersion());

        mockMvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isUnauthorized());

        assertThrows(BusinessException.class,
                () -> authService.verifyEmailCode(pendingLogin.getChallengeId(), "123456"));
        assertNotNull(challengeRepository.findByChallengeId(
                        UUID.fromString(pendingLogin.getChallengeId()))
                .orElseThrow().getRevokedAt());
    }

    @Test
    void passwordChangeRejectsPendingResetOtpAndInvalidatesPriorJwt() throws Exception {
        User user = createUser(uniqueEmail("change-invalidates-session"));
        String oldJwt = tokenFor(user);
        EmailOtpChallengeResponse pendingLogin =
                emailOtpService.issueChallenge(user, EmailOtpPurpose.LOGIN);
        EmailOtpChallengeResponse pendingReset =
                authService.requestPasswordReset(user.getEmail());

        mockMvc.perform(csrfPost("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + oldJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Password123",
                                "newPassword", "AnotherPassword456"))))
                .andExpect(status().isOk());

        User changed = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertTrue(passwordEncoder.matches("AnotherPassword456", changed.getPassword()));
        assertEquals(1L, changed.getAuthVersion());

        mockMvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isUnauthorized());

        assertThrows(BusinessException.class,
                () -> authService.verifyEmailCode(pendingLogin.getChallengeId(), "123456"));
        assertThrows(BusinessException.class,
                () -> authService.resetPassword(
                        pendingReset.getChallengeId(), "123456", "StalePassword987"));

        User afterStaleReset = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertTrue(passwordEncoder.matches("AnotherPassword456", afterStaleReset.getPassword()),
                "A reset OTP issued before the password change must not overwrite the new password");
        assertNotNull(challengeRepository.findByChallengeId(
                        UUID.fromString(pendingLogin.getChallengeId()))
                .orElseThrow().getRevokedAt());
        assertNotNull(challengeRepository.findByChallengeId(
                        UUID.fromString(pendingReset.getChallengeId()))
                .orElseThrow().getRevokedAt());
    }

    private MockHttpServletRequestBuilder csrfPost(String path) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie tokenCookie = result.getResponse().getCookie("XSRF-TOKEN");
        String tokenCookieHeader = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElse(null);
        String token = tokenCookie != null
                ? tokenCookie.getValue()
                : tokenCookieHeader == null
                        ? null
                        : tokenCookieHeader.substring("XSRF-TOKEN=".length()).split(";", 2)[0];
        assertNotNull(token, "CSRF bootstrap must issue XSRF-TOKEN through the servlet cookie or Set-Cookie header; status=" + result.getResponse().getStatus() + ", headerNames=" + result.getResponse().getHeaderNames() + ", Set-Cookie=" + result.getResponse().getHeaders("Set-Cookie") + ", X-XSRF-TOKEN=" + result.getResponse().getHeader("X-XSRF-TOKEN") + ", cookies=" + java.util.Arrays.toString(result.getResponse().getCookies()));
        assertFalse(token.isBlank(), "CSRF bootstrap must issue a non-empty XSRF-TOKEN");
        assertEquals(token, result.getResponse().getHeader("X-XSRF-TOKEN"),
                "CSRF response header must match the persisted raw CSRF token");
        return post(path)
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token);
    }

    private User createUser(String email) {
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("ROLE_USER").description("Standard customer").build()));
        User user = User.builder()
                .firstName("Test")
                .lastName("Account")
                .email(email)
                .password(passwordEncoder.encode("Password123"))
                .isEnabled(true)
                .emailVerified(true)
                .role(role)
                .build();
        return userRepository.saveAndFlush(user);
    }

    private String tokenFor(User user) {
        var details = new AccountUserDetails(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getIsEnabled()),
                true,
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getName())),
                user.getAuthVersion());
        return jwtService.generateToken(details);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
