package com.store.inventory.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SupplierInventoryResponse {

    private List<Item> items;

    @Data
    public static class Item {
        // Normalize a supplier-provided ID without confusing it with supplierSku.
        @JsonAlias({"productId", "externalProductId", "id"})
        private String supplierProductId;
        private String supplierSku;
        private Integer quantity;
        private BigDecimal unitCost;
        private String currency;
        private LocalDateTime observedAt;
    }
}
