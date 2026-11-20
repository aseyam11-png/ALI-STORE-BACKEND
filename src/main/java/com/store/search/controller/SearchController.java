package com.store.search.controller;

import com.store.common.response.ApiResponse;
import com.store.search.dto.SearchSuggestionsResponse;
import com.store.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<SearchSuggestionsResponse>> suggestions(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                searchService.suggestions(q, limit),
                "Search suggestions generated successfully"));
    }
}
