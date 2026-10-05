package com.store.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.ai.config.AiProperties;
import com.store.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient implements AiProviderClient {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        ensureConfigured();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());

        String baseUrl = properties.getBaseUrl().trim().replaceAll("/+$", "");
        String endpoint = baseUrl + "/chat/completions";
        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .build();

        Map<String, Object> body = Map.of(
                "model", properties.getModel().trim(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            String raw = client.post()
.uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new ServiceUnavailableException("AI provider returned an empty response");
            }
            return content.asText();
        } catch (RestClientException | java.io.IOException ex) {
            throw new ServiceUnavailableException("Ask Ali is temporarily unavailable");
        }
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new ServiceUnavailableException("Ask Ali is not enabled");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getModel() == null || properties.getModel().isBlank()) {
            throw new ServiceUnavailableException("Ask Ali is not configured");
        }
        if (properties.getTimeoutMs() < 1000 || properties.getTimeoutMs() > 30000) {
            throw new ServiceUnavailableException("AI provider timeout configuration is invalid");
        }
    }
}
