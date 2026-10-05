package com.store.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskAliRequest(
        @NotBlank(message = "Prompt is required")
        @Size(max = 1200, message = "Prompt must be 1200 characters or less")
        String prompt
) {
}
