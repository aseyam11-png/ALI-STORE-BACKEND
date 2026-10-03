package com.store.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SupplierInventoryResponse {

    private List<Item> items;

    @Data
    public static class Item {
        private String supplierSku;
        private Integer quantity;
        private BigDecimal unitCost;
        private String currency;
        private LocalDateTime observedAt;
    }
}
