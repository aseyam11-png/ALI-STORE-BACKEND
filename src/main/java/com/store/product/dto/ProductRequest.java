package com.store.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String slug;
    private String description;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.00", message = "Selling price must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Selling price must have at most 2 decimal places")
    private BigDecimal price;

    @Pattern(regexp = "[A-Za-z]{3}", message = "Currency must be a 3-letter ISO-style code")
    private String currency;

    @NotBlank(message = "Store SKU is required")
    private String sku;

    private String imageUrl;
    @NotNull(message = "Category ID is required")
    private Long categoryId;
    private Long brandId;
    private Long supplierId;
    private String supplierSku;

    @DecimalMin(value = "0.01", message = "Supplier cost must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Supplier cost must have at most 2 decimal places")
    private BigDecimal supplierCost;
}
