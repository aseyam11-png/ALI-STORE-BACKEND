package com.store.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Server-side Cloudflare Turnstile verification. Tokens are never trusted based
 * on the browser response alone, and the verifier only calls Cloudflare's fixed
 * siteverify endpoint.
 */
@Component
public class CloudflareTurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(CloudflareTurnstileVerifier.class);
    private static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestTemplate restTemplate;

    @Value("${app.auth.turnstile-secret-key:}")
    private String secretKey;

    @Value("${app.auth.turnstile-expected-hostname:}")
    private String expectedHostname;

    public CloudflareTurnstileVerifier(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public boolean verify(String token) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(secretKey)) {
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token.trim());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<SiteVerifyResponse> response = restTemplate.postForEntity(
                    SITEVERIFY_URL,
                    new HttpEntity<>(form, headers),
                    SiteVerifyResponse.class);

            SiteVerifyResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || !body.success()) {
                return false;
            }

            return !StringUtils.hasText(expectedHostname)
                    || expectedHostname.trim().equalsIgnoreCase(body.hostname());
        } catch (RestClientException ex) {
            // Do not log the secret or the one-time response token.
            log.warn("Cloudflare Turnstile verification request failed ({})",
                    ex.getClass().getSimpleName());
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SiteVerifyResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("hostname") String hostname) {
    }
}
