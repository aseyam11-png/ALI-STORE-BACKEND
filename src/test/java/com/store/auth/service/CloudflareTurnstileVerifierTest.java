package com.store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudflareTurnstileVerifierTest {

    private static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Test
    void acceptsSuccessfulSiteverifyResponseForExpectedHostname() {
        CloudflareTurnstileVerifier verifier = verifier("shop.example.com");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(verifier, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SITEVERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("secret=turnstile-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("response=one-time-token")))
                .andRespond(withSuccess(
                        "{\"success\":true,\"hostname\":\"shop.example.com\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(verifier.verify("one-time-token"));
        server.verify();
    }

    @Test
    void rejectsSuccessfulChallengeForUnexpectedHostname() {
        CloudflareTurnstileVerifier verifier = verifier("shop.example.com");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(verifier, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SITEVERIFY_URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"hostname\":\"attacker.example.net\"}",
                        MediaType.APPLICATION_JSON));

        assertFalse(verifier.verify("one-time-token"));
        server.verify();
    }

    @Test
    void failsClosedWhenTokenOrSecretIsMissing() {
        CloudflareTurnstileVerifier verifier = verifier("shop.example.com");
        ReflectionTestUtils.setField(verifier, "secretKey", "");
        assertFalse(verifier.verify("one-time-token"));
        assertFalse(verifier.verify(" "));
    }

    private CloudflareTurnstileVerifier verifier(String hostname) {
        CloudflareTurnstileVerifier verifier = new CloudflareTurnstileVerifier(new RestTemplateBuilder());
        ReflectionTestUtils.setField(verifier, "secretKey", "turnstile-secret");
        ReflectionTestUtils.setField(verifier, "expectedHostname", hostname);
        return verifier;
    }
}
