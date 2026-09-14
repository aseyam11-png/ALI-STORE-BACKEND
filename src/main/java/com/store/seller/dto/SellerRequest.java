package com.store.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SellerRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Store name is required")
    private String storeName;

    private String taxId;
}