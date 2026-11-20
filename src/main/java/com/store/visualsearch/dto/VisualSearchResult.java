package com.store.visualsearch.dto;

import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record VisualSearchResult(
        Long productId, String productName, String image, int similarity,
        BigDecimal price, String currency, String brand, String category) {}
