package com.store.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
public class SupplierInventoryUpdateRequest {
    @NotBlank(message = "Supplier code is required")
    private String supplierCode;

    @NotBlank(message = "Supplier SKU is required")
    private String supplierSku;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;

    @DecimalMin(value = "0.00", message = "Supplier price cannot be negative")
    private BigDecimal supplierPrice;

    private LocalDateTime syncedAt;
}
