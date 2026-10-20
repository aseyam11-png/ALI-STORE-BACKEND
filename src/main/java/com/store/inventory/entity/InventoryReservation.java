package com.store.inventory.entity;

import com.store.common.entity.BaseEntity;
import com.store.inventory.enums.InventoryReservationStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_reservation_order_product",
                columnNames = {"order_id", "product_id"}),
        indexes = @Index(name = "idx_inventory_reservation_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryReservationStatus status;
}
