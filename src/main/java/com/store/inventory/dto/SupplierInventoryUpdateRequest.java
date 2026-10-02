package com.store.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SupplierInventoryUpdateRequest {
    @NotBlank
    private String supplierCode;

    @NotBlank
    private String supplierSku;

    @NotNull
    private Long productId;

    @NotNull
    @Min(0)
    private Integer quantity;
}
