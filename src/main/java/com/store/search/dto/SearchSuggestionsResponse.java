package com.store.search.dto;

import java.util.List;

public record SearchSuggestionsResponse(
        String query,
        List<SearchSuggestionResponse> suggestions
) {
}
