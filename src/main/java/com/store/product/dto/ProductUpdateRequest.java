package com.store.product.dto;

import com.store.product.enums.ProductStatus;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Partial update - every field is optional; only non-null fields are applied.
 * Used by the admin-only PATCH endpoint.
 */
@Data
public class ProductUpdateRequest {

    private String name;
    private String description;

    @Min(value = 0, message = "Price must be positive")
    private BigDecimal price;

    private String currency;
    private Long categoryId;
    private Long brandId;
    private ProductStatus status;
}
