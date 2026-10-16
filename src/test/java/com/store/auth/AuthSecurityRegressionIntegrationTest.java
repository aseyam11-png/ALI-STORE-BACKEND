package com.store.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.dto.LoginRequest;
import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import com.store.auth.repository.EmailOtpChallengeRepository;
import com.store.auth.service.AccountUserDetails;
import com.store.auth.service.AuthService;
import com.store.auth.service.EmailOtpSender;
import com.store.auth.service.JwtService;
import com.store.common.exception.UnauthorizedException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;

    @MockBean
    private EmailOtpSender emailOtpSender;

    @Test
    void invalidPasswordResetCodeAttemptPersistsAfterRequestReturnsBadRequest() throws Exception {
        User user = createUser(uniqueEmail("otp-counter"));
        clearInvocations(emailOtpSender);

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", user.getEmail()))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for this email, password reset instructions will be sent."));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(
                eq(user.getEmail()),
                codeCaptor.capture(),
                eq(EmailOtpPurpose.PASSWORD_RESET),
                any());

        EmailOtpChallenge challenge = challengeRepository
                .findTopByUserIdAndPurposeAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                        user.getId(), EmailOtpPurpose.PASSWORD_RESET)
                .orElseThrow();

        String correctCode = codeCaptor.getValue();
        String wrongCode = "000000".equals(correctCode) ? "000001" : "000000";

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeId", challenge.getChallengeId().toString(),
                                "code", wrongCode,
                                "newPassword", "NewPassword123"))))
                .andExpect(status().isBadRequest());

        EmailOtpChallenge persisted = challengeRepository
                .findTopByUserIdAndPurposeAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                        user.getId(), EmailOtpPurpose.PASSWORD_RESET)
                .orElseThrow();
        assertEquals(1, persisted.getAttemptCount(),
                "A wrong OTP must increment the persisted counter even though the endpoint returns an error");
    }

    @Test
    void passwordResetRequestReturnsSamePublicShapeForExistingAndUnknownEmails() throws Exception {
        User existing = createUser(uniqueEmail("reset-enumeration"));
        clearInvocations(emailOtpSender);

        String existingJson = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", existing.getEmail()))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = uniqueEmail("missing-reset");
        String unknownJson = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", unknownEmail))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        JsonNode known = objectMapper.readTree(existingJson);
        JsonNode unknown = objectMapper.readTree(unknownJson);
        assertEquals(known.path("message").asText(), unknown.path("message").asText());
        assertEquals(known.path("data").path("requiresEmailVerification").asBoolean(),
                unknown.path("data").path("requiresEmailVerification").asBoolean());
        assertEquals("PASSWORD_RESET", known.path("data").path("purpose").asText());
        assertEquals("PASSWORD_RESET", unknown.path("data").path("purpose").asText());
        assertTrue(known.path("data").path("challengeId").isTextual());
        assertTrue(unknown.path("data").path("challengeId").isTextual());
        assertNotEquals(known.path("data").path("challengeId").asText(),
                unknown.path("data").path("challengeId").asText());
        assertNotEquals("", known.path("data").path("maskedEmail").asText());
        assertNotEquals("", unknown.path("data").path("maskedEmail").asText());
        assertTrue(known.path("data").path("expiresInSeconds").isNull());
        assertTrue(unknown.path("data").path("expiresInSeconds").isNull());

        verify(emailOtpSender, times(1)).send(
                eq(existing.getEmail()),
                anyString(),
                eq(EmailOtpPurpose.PASSWORD_RESET),
                any());
    }

    @Test
    void passwordResetConsumesOtpChangesPasswordAndInvalidatesExistingJwt() throws Exception {
        User user = createUser(uniqueEmail("password-reset"));
        String token = tokenFor(user);
        clearInvocations(emailOtpSender);

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", user.getEmail()))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).send(
                eq(user.getEmail()),
                codeCaptor.capture(),
                eq(EmailOtpPurpose.PASSWORD_RESET),
                any());

        EmailOtpChallenge challenge = challengeRepository
                .findTopByUserIdAndPurposeAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                        user.getId(), EmailOtpPurpose.PASSWORD_RESET)
                .orElseThrow();

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeId", challenge.getChallengeId().toString(),
                                "code", codeCaptor.getValue(),
                                "newPassword", "NewPassword456"))))
                .andExpect(status().isOk());

        User updated = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPassword456", updated.getPassword()));
        assertTrue(Boolean.TRUE.equals(updated.getEmailVerified()));
        assertEquals(1L, updated.getAuthVersion());
        assertNotNull(challengeRepository.findForVerification(challenge.getChallengeId())
                .orElseThrow().getConsumedAt());

        mockMvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotReuseAnUnexpiredJwt() throws Exception {
        User user = createUser(uniqueEmail("disabled-session"));
        String token = tokenFor(user);

        mockMvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        user.setIsEnabled(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeInvalidatesExistingJwtAndUpdatesPassword() throws Exception {
        User user = createUser(uniqueEmail("change-password"));
        String token = tokenFor(user);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Password123",
                                "newPassword", "AnotherPassword456"))))
                .andExpect(status().isOk());

        User updated = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertTrue(passwordEncoder.matches("AnotherPassword456", updated.getPassword()));
        assertEquals(1L, updated.getAuthVersion());

        mockMvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationDoesNotRevealWhetherEmailAlreadyExists() throws Exception {
        String email = uniqueEmail("enumeration");
        createUser(email);

        Map<String, String> request = Map.of(
                "firstName", "Ali",
                "lastName", "Store",
                "email", email,
                "password", "Password123");

        String duplicateResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String newEmail = uniqueEmail("new-registration");
        Map<String, String> newRequest = Map.of(
                "firstName", "Ali",
                "lastName", "Store",
                "email", newEmail,
                "password", "Password123");

        String newResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newRequest)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        JsonNode duplicate = objectMapper.readTree(duplicateResponse);
        JsonNode newlyRegistered = objectMapper.readTree(newResponse);
        assertEquals(duplicate.path("message").asText(), newlyRegistered.path("message").asText());
        assertEquals(duplicate.path("data").path("requiresEmailVerification").asBoolean(),
                newlyRegistered.path("data").path("requiresEmailVerification").asBoolean());
        assertEquals(duplicate.path("data").path("purpose").asText(),
                newlyRegistered.path("data").path("purpose").asText());
        assertNotEquals(duplicate.path("data").path("challengeId").asText(),
                newlyRegistered.path("data").path("challengeId").asText());
    }

    @Test
    void fifthFailedLoginLocksAccountAndReturnsSameUnauthorizedError() {
        User user = createUser(uniqueEmail("account-lockout"));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(UnauthorizedException.class, () -> authService.login(
                    LoginRequest.builder()
                            .email(user.getEmail())
                            .password("WrongPassword123")
                            .build()));
        }

        User locked = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertNotNull(locked.getLoginLockedUntil());
        assertTrue(locked.getLoginLockedUntil().isAfter(java.time.Instant.now()));

        assertThrows(UnauthorizedException.class, () -> authService.login(
                LoginRequest.builder()
                        .email(user.getEmail())
                        .password("Password123")
                        .build()));
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
        return userRepository.save(user);
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
