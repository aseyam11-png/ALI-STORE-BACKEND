package com.store.ai.dto;

import com.store.inventory.enums.PublicAvailability;

import java.math.BigDecimal;
import java.util.List;

public record AiProductResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        String currency,
        PublicAvailability availability,
        Integer availableQuantity,
        Long categoryId,
        String categoryName,
        String categorySlug,
        Long brandId,
        String brandName,
        List<String> images,
        String primaryImageUrl
) {
}