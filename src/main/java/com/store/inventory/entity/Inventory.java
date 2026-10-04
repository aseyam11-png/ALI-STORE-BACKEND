package com.store.inventory.entity;

import com.store.common.entity.BaseEntity;
import com.store.inventory.enums.InventorySource;
import com.store.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Column(name = "low_stock_threshold")
    @Builder.Default
    private Integer lowStockThreshold = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30)
    private InventorySource source;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "supplier_reported_quantity")
    private Integer supplierReportedQuantity;

    @Column(name = "inventory_discrepancy", nullable = false)
    @Builder.Default
    private Boolean inventoryDiscrepancy = false;

    @Version
    private Long version;
}
