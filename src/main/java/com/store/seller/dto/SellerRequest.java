package com.store.seller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SellerRequest {

    @NotBlank(message = "Store name is required")
    private String storeName;

    private String taxId;
}
