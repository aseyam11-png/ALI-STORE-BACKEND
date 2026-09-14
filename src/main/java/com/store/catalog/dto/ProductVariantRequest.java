package com.store.catalog.dto;

import com.store.catalog.enums.VariantStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVariantRequest {
    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "SKU is required")
    private String sku;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    private String color;
    private String size;
    private VariantStatus status;
}