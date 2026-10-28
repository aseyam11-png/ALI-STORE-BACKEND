package com.store.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.store.auth.dto.LoginRequest;
import com.store.common.exception.ServiceUnavailableException;
import com.store.user.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AdminLoginGateService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String TURNSTILE_VERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-gate.enabled:false}")
    private boolean enabled;

    @Value("${app.security.admin-gate.password-hash:}")
    private String passwordHash;

    @Value("${app.security.admin-gate.turnstile-secret:}")
    private String turnstileSecret;

    @Value("${app.security.admin-gate.turnstile-hostname:}")
    private String expectedHostname;

    @PostConstruct
    void validateProductionConfiguration() {
        if (enabled
                && (isBlank(passwordHash)
                || !passwordHash.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}")
                || isBlank(turnstileSecret)
                || isBlank(expectedHostname))) {
            throw new IllegalStateException(
                    "A valid BCrypt cost-12 ADMIN_GATE_PASSWORD_HASH, TURNSTILE_SECRET_KEY, and "
                            + "TURNSTILE_HOSTNAME must be configured before enabling the administrator gate");
        }
    }

    /**
     * Returns true only after the additional administrator password and Turnstile
     * checks succeed. Ordinary customers never need these extra credentials.
     */
    public boolean verifyIfAdmin(User user, LoginRequest request, String clientIp) {
        if (user.getRole() == null || !ADMIN_ROLE.equals(user.getRole().getName())) {
            return false;
        }

        if (!enabled) {
            return false;
        }

        if (isBlank(passwordHash) || isBlank(turnstileSecret) || isBlank(expectedHostname)) {
            throw new ServiceUnavailableException(
                    "Administrator security gate is not configured. Contact the system operator.");
        }

        String captchaToken = request.getCaptchaToken();
        String secondPassword = request.getAdminGatePassword();
        if (isBlank(secondPassword)
                || secondPassword.getBytes(StandardCharsets.UTF_8).length < 16
                || secondPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !safePasswordMatches(secondPassword, passwordHash)
                || isBlank(captchaToken)
                || !verifyTurnstile(captchaToken, clientIp)) {
            throw new BadCredentialsException("Invalid credentials or security verification failed");
        }

        return true;
    }

    private boolean safePasswordMatches(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean verifyTurnstile(String token, String clientIp) {
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(2500);
            requestFactory.setReadTimeout(2500);
            RestClient client = RestClient.builder().requestFactory(requestFactory).build();

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", turnstileSecret);
            form.add("response", token);
            if (!isBlank(clientIp) && !"unknown".equals(clientIp)) {
                form.add("remoteip", clientIp);
            }

            TurnstileResponse response = client.post()
                    .uri(TURNSTILE_VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TurnstileResponse.class);

            return response != null
                    && response.success()
                    && "admin_login".equals(response.action())
                    && expectedHostname.trim().equalsIgnoreCase(
                            response.hostname() == null ? "" : response.hostname().trim());
        } catch (RestClientException | IllegalArgumentException ex) {
            // Fail closed if Cloudflare rejects the token or cannot be reached.
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TurnstileResponse(boolean success, String hostname, String action) { }
}
