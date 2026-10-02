package com.store.product.dto;

import com.store.product.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductUpdateRequest {

    private String name;
    private String description;

    @DecimalMin(value = "0.00", message = "Selling price must not be negative")
    private BigDecimal price;

    private String currency;
    private Long categoryId;
    private Long brandId;

    private Long supplierId;
    private String supplierSku;

    @DecimalMin(value = "0.01", message = "Supplier cost must be greater than zero")
    private BigDecimal supplierCost;

    private ProductStatus status;
}
