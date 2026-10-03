package com.store.inventory.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class SupplierInventorySyncReport {
    Long supplierId;
    String supplierCode;
    int receivedItems;
    int matchedProducts;
    int updatedProducts;
    int rejectedItems;
    LocalDateTime syncedAt;
    List<String> errors;
}
