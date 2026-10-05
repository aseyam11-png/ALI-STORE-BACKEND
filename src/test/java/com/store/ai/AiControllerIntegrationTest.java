package com.store.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.ai.client.AiProviderClient;
import com.store.ai.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiProperties properties;

    @MockBean
    private AiProviderClient aiProviderClient;

    @Test
    void askIsPublicButUnavailableUntilConfigured() throws Exception {
        properties.setEnabled(false);

        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("prompt", "هاتف مناسب"))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void askRejectsOversizedPromptAtBoundary() throws Exception {
        String prompt = "x".repeat(1201);

        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("prompt", prompt))))
                .andExpect(status().isBadRequest());
    }
}