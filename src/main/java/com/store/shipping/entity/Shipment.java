package com.store.shipping.entity;

import com.store.common.entity.BaseEntity;
import com.store.shipping.entity.ShipmentStatus;
import com.store.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipments_order_id", columnList = "order_id"),
        @Index(name = "idx_shipments_supplier_id", columnList = "supplier_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "supplier_order_id", unique = true, length = 255)
    private String supplierOrderId;

    @Column(name = "tracking_number", unique = true, length = 100)
    private String trackingNumber;

    @Column(name = "carrier", length = 100)
    private String carrier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    private ShippingAddress shippingAddress;

    @Column(name = "shipping_address_snapshot", length = 1000)
    private String shippingAddressSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}
