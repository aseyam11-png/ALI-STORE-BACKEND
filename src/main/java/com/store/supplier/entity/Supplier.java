package com.store.supplier.entity;

import com.store.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String externalCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "api_base_url", length = 500)
    private String apiBaseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @Column(name = "fulfillment_enabled", nullable = false)
    @Builder.Default
    private Boolean fulfillmentEnabled = true;

    @Column(name = "inventory_sync_enabled", nullable = false)
    @Builder.Default
    private Boolean inventorySyncEnabled = true;
}
