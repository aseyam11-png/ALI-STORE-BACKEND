package com.store.inventory.dto;

import com.store.inventory.enums.InventorySource;
import com.store.inventory.enums.PublicAvailability;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InventoryResponse {
    private Long id;
    private Long productId;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Integer lowStockThreshold;
    private Boolean isLowStock;
    private PublicAvailability availability;
    private InventorySource source;
    private LocalDateTime lastSyncedAt;
    private Integer supplierReportedQuantity;
    private Boolean inventoryDiscrepancy;
}
