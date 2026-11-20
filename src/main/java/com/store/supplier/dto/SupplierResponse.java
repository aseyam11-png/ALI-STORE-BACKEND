package com.store.supplier.dto;

import com.store.supplier.entity.SupplierStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SupplierResponse {
    private Long id;
    private String externalCode;
    private String name;
    private String apiBaseUrl;
    private SupplierStatus status;
    private Boolean fulfillmentEnabled;
    private Boolean inventorySyncEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
