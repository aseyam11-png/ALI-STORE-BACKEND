package com.store.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityCsrfContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void csrfBootstrapIsPublicAndIssuesBrowserReadableTokenCookie() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("CSRF token retrieved successfully"));
    }

    @Test
    void hmacProtectedSupplierWebhookIsNotBlockedByBrowserCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/suppliers/shipments/update")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
