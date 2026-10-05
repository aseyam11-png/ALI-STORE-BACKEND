package com.store.ai.dto;

import com.store.product.dto.ProductResponse;

import java.util.List;

public record AskAliResponse(
        String answer,
        String suggestedQuery,
        List<ProductResponse> products,
        boolean aiGenerated
) {
}
