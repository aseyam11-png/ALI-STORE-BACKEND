package com.store.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityCorsContractTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredFrontendMaySendCheckoutIdempotencyAndCsrfHeaders() {
        CorsConfiguration configuration = corsConfigurationSource
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/orders"));

        assertTrue(configuration != null, "CORS configuration must be available");
        assertTrue(configuration.getAllowedHeaders().contains(HttpHeaders.AUTHORIZATION));
        assertTrue(configuration.getAllowedHeaders().contains(HttpHeaders.CONTENT_TYPE));
        assertTrue(configuration.getAllowedHeaders().contains("Idempotency-Key"));
        assertTrue(configuration.getAllowedHeaders().contains("X-XSRF-TOKEN"));
        assertTrue(configuration.getAllowedMethods().containsAll(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")));
        assertTrue(Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }

    @Test
    void spaPreflightAllowsCredentialedRequestWithXsrfHeader() throws Exception {
        mockMvc.perform(options("/api/v1/orders")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void retryAfterIsDeclaredForBrowserReadableRateLimitFeedback() {
        CorsConfiguration configuration = corsConfigurationSource
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertTrue(configuration != null, "CORS configuration must be available");
        assertTrue(configuration.getExposedHeaders().contains("Retry-After"));
        assertTrue(configuration.getExposedHeaders().contains("X-XSRF-TOKEN"),
                "The SPA must be able to read the CSRF header across origins");
        assertTrue(configuration.getMaxAge() != null && configuration.getMaxAge() == 3600L);
    }
}
