package com.store.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierRequest {

    @NotBlank(message = "External supplier code is required")
    private String externalCode;

    @NotBlank(message = "Supplier name is required")
    private String name;

    private String apiBaseUrl;
    private Boolean fulfillmentEnabled;
    private Boolean inventorySyncEnabled;
}
