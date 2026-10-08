package com.store.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductVariantRequest {
    @NotBlank(message = "Variant SKU is required")
    private String sku;
    @NotBlank(message = "Variant option name is required")
    private String optionName;
    @NotBlank(message = "Variant option value is required")
    private String optionValue;
    @DecimalMin(value = "0.00", message = "Variant price cannot be negative")
    private BigDecimal price;
    @Min(value = 0, message = "Variant stock cannot be negative")
    private Integer availableQuantity = 0;
    private String imageUrl;
    private Boolean active = true;
}