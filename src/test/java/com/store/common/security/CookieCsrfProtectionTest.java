
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

import java.util.Arrays;

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

    @Autowired
    private MockMvc mockMvc;

    private Cookie findCsrfCookie(MvcResult result) {
        return Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .findFirst()
                .orElse(null);
    }

    @Test
    void csrfEndpointIssuesReadableXsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = findCsrfCookie(result);

        assertNotNull(
                csrfCookie,
                "CSRF endpoint must issue the XSRF-TOKEN cookie");

        assertEquals(
                "/",
                csrfCookie.getPath(),
                "The CSRF cookie must be available across application routes");

        assertEquals(
                "Strict",
                csrfCookie.getAttribute("SameSite"),
                "The CSRF cookie must explicitly use SameSite=Strict");

        assertFalse(
                csrfCookie.isHttpOnly(),
                "JavaScript-readable CSRF cookies must not be HttpOnly");
    }

    @Test
    void cookieAuthenticatedUnsafeRequestWithoutCsrfTokenIsRejected()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(new Cookie(
                                "ali_store_session",
                                "invalid-session-cookie"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousPasswordResetRequestWithoutCsrfTokenIsRejected()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"victim@example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void matchingCookieAndHeaderPassCsrfFilterBeforeAuthentication()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = findCsrfCookie(result);

        assertNotNull(
                csrfCookie,
                "CSRF endpoint must issue the XSRF-TOKEN cookie");

        String token = csrfCookie.getValue();

        assertTrue(
                token != null && !token.isBlank(),
                "The CSRF token value must not be empty");

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(
                                new Cookie(
                                        "ali_store_session",
                                        "invalid-session-cookie"),
                                new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
