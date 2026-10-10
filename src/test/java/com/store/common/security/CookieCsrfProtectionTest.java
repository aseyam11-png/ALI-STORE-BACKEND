package com.store.common.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CookieCsrfProtectionTest {

    private static final Pattern CSRF_COOKIE =
            Pattern.compile("(?:^|;\\s*)XSRF-TOKEN=([^;]+)");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void csrfEndpointIssuesReadableXsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie, "CSRF endpoint must issue the XSRF-TOKEN cookie");
        assertTrue(setCookie.contains("XSRF-TOKEN="));
        assertFalse(setCookie.toLowerCase().contains("httponly"), "JavaScript-readable CSRF cookies must not be HttpOnly");
    }

    @Test
    void cookieAuthenticatedUnsafeRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/suppliers/shipments/update")
                        .cookie(new Cookie("ali_store_session", "invalid-session-cookie"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void matchingCookieAndHeaderPassCsrfFilterBeforeWebhookAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        Matcher matcher = CSRF_COOKIE.matcher(setCookie);
        assertTrue(matcher.find(), "Set-Cookie must contain a parseable XSRF-TOKEN");
        String token = matcher.group(1);

        mockMvc.perform(post("/api/v1/integrations/suppliers/shipments/update")
                        .cookie(
                                new Cookie("ali_store_session", "invalid-session-cookie"),
                                new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
