package com.store.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class SecurityCorsContractTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void configuredFrontendMaySendCheckoutIdempotencyHeader() {
        CorsConfiguration configuration = corsConfigurationSource
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/orders"));

        assertTrue(configuration != null, "CORS configuration must be available");
        assertTrue(configuration.getAllowedHeaders().contains(HttpHeaders.AUTHORIZATION));
        assertTrue(configuration.getAllowedHeaders().contains(HttpHeaders.CONTENT_TYPE));
        assertTrue(configuration.getAllowedHeaders().contains("Idempotency-Key"));
        assertTrue(configuration.getAllowedMethods().containsAll(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")));
        assertTrue(Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }

    @Test
    void retryAfterIsDeclaredForBrowserReadableRateLimitFeedback() {
        CorsConfiguration configuration = corsConfigurationSource
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertTrue(configuration != null, "CORS configuration must be available");
        assertTrue(configuration.getExposedHeaders().contains("Retry-After"));
        assertTrue(configuration.getMaxAge() != null && configuration.getMaxAge() == 3600L);
    }
}
