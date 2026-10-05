package com.store.ai.dto;


import java.util.List;

public record AskAliResponse(
        String answer,
        String suggestedQuery,
        List<AiProductResponse> products,
        boolean aiGenerated
) {
}
