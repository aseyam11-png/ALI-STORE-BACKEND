package com.store.inventory.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierInventoryItem {
    private String supplierSku;
    private Integer quantity;
    private LocalDateTime syncedAt;
}
