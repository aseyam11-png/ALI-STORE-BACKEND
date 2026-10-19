package com.store.inventory.entity;

import com.store.common.entity.BaseEntity;
import com.store.inventory.enums.InventoryReservationStatus;
import com.store.order.entity.OrderItem;
import com.store.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_reservations_order_item", columnNames = "order_item_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InventoryReservationStatus status;
}
