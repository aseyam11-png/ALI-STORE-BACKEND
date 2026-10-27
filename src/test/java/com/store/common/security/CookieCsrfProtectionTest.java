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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        String setCookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElse(null);
        assertTrue(csrfCookie != null || setCookie != null,
                "CSRF endpoint must issue XSRF-TOKEN as a servlet cookie or Set-Cookie header");
        String token = csrfTokenFrom(result);
        assertNotNull(result.getResponse().getHeader("X-XSRF-TOKEN"),
                "The bootstrap endpoint must expose a CORS-readable CSRF token header");
        assertEquals(token, result.getResponse().getHeader("X-XSRF-TOKEN"),
                "The response header must match the persisted raw CSRF cookie token");
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        assertTrue((csrfCookie != null && "/".equals(csrfCookie.getPath()))
                        || (setCookie != null && setCookie.contains("Path=/")),
                "The SPA must be able to read the CSRF cookie on application routes");
        assertTrue((csrfCookie != null && "Strict".equalsIgnoreCase(csrfCookie.getAttribute("SameSite")))
                        || (setCookie != null && setCookie.contains("SameSite=Strict")),
                "SameSite=Strict must be configured explicitly and preserved by the servlet cookie or Set-Cookie header");
        assertTrue((csrfCookie == null || !csrfCookie.isHttpOnly())
                        && (setCookie == null || !setCookie.toLowerCase().contains("httponly")),
                "JavaScript-readable CSRF cookies must not be HttpOnly");
    }

    @Test
    void cookieAuthenticatedUnsafeRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(new Cookie("ali_store_session", "invalid-session-cookie"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousPasswordResetRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"victim@example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void mismatchedCookieAndHeaderAreRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        String token = csrfTokenFrom(result);

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token + "-mismatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"victim@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void matchingCookieAndHeaderPassCsrfFilterBeforeAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        String token = csrfTokenFrom(result);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(
                                new Cookie("ali_store_session", "invalid-session-cookie"),
                                new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String csrfTokenFrom(MvcResult result) {
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (csrfCookie != null && csrfCookie.getValue() != null && !csrfCookie.getValue().isBlank()) {
            return csrfCookie.getValue();
        }
        for (String cookieHeader : result.getResponse().getHeaders("Set-Cookie")) {
            Matcher matcher = CSRF_COOKIE.matcher(cookieHeader);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("CSRF bootstrap must issue XSRF-TOKEN via Cookie or Set-Cookie; headers="
                + result.getResponse().getHeaders("Set-Cookie"));
    }
}
