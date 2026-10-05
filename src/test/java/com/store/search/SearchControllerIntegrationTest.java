package com.store.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void suggestionsArePublicAndBounded() throws Exception {
        mockMvc.perform(get("/api/v1/search/suggestions")
                        .param("q", "phone")
                        .param("limit", "99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("phone"))
                .andExpect(jsonPath("$.data.suggestions.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(12)));
    }

    @Test
    void suggestionsRejectQueriesThatExceedTheSafetyBudget() throws Exception {
        mockMvc.perform(get("/api/v1/search/suggestions")
                        .param("q", "x".repeat(121)))
                .andExpect(status().isBadRequest());
    }
}