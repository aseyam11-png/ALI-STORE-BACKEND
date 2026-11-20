package com.store.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.dto.AuthResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthResponseSecurityContractTest {

    @Test
    void tokenIsNotSerializedEvenWhenAuthResponseContainsItForCookieCreation() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .token("sensitive.jwt.value")
                .email("user@example.com")
                .role("ROLE_USER")
                .requiresEmailVerification(false)
                .build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertFalse(json.contains("sensitive.jwt.value"));
        assertFalse(json.contains("\"token\""));
        assertTrue(json.contains("user@example.com"));
    }
}
