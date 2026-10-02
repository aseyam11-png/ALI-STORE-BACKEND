package com.store.supplier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierProductPriceUpdateRequest {
    @NotBlank(message = "Supplier code is required")
    private String supplierCode;

    @NotBlank(message = "Supplier SKU is required")
    private String supplierSku;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Supplier cost is required")
    @DecimalMin(value = "0.01", message = "Supplier cost must be greater than zero")
    private BigDecimal supplierCost;
}
