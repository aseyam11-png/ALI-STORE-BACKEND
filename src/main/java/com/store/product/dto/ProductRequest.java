package com.store.product.dto;

import jakarta.validation.constraints.DecimalMin;
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

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.00", message = "Selling price must not be negative")
    private BigDecimal price;

    private String currency;

    @NotBlank(message = "Store SKU is required")
    private String sku;

    private String imageUrl;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private Long brandId;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotBlank(message = "Supplier SKU is required")
    private String supplierSku;

    @NotNull(message = "Supplier cost is required")
    @DecimalMin(value = "0.01", message = "Supplier cost must be greater than zero")
    private BigDecimal supplierCost;
}
