package com.store.search.dto;

import lombok.Builder;

@Builder
public record SearchSuggestionResponse(
        String type,
        String id,
        String title,
        String subtitle,
        String image,
        String href,
        int score
) {
}
