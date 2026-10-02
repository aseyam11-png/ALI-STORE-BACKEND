package com.store.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String slug;

    private String description;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be positive")
    private BigDecimal price;

    private String currency;

    @NotBlank(message = "SKU is required")
    private String sku;

    private String imageUrl;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private Long brandId;
}
