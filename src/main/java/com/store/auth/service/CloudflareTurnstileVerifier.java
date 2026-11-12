package com.store.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class CloudflareTurnstileVerifier implements TurnstileVerifier {

    private static final String VERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final String EXPECTED_ACTION = "admin_login";

    @Override
    public boolean verify(String token, String clientIp, String secret, String expectedHostname) {
        if (isBlank(token) || isBlank(secret) || isBlank(expectedHostname)) {
            return false;
        }

        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(2500);
            requestFactory.setReadTimeout(2500);
            RestClient client = RestClient.builder().requestFactory(requestFactory).build();

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secret);
            form.add("response", token);
            if (!isBlank(clientIp) && !"unknown".equals(clientIp)) {
                form.add("remoteip", clientIp);
            }

            TurnstileResponse response = client.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TurnstileResponse.class);

            return response != null
                    && response.success()
                    && EXPECTED_ACTION.equals(response.action())
                    && expectedHostname.trim().equalsIgnoreCase(
                            response.hostname() == null ? "" : response.hostname().trim());
        } catch (RestClientException | IllegalArgumentException ex) {
            // Fail closed: a provider/network failure cannot authorize an administrator.
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TurnstileResponse(boolean success, String hostname, String action) {
    }
}
