package com.store.catalog.dto;

import com.store.catalog.enums.VariantStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductVariantResponse {
    private Long id;
    private String sku;
    private BigDecimal price;
    private String color;
    private String size;
    private VariantStatus status;
    private Long productId;
}