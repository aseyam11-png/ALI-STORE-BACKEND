package com.store.shipping.dto;

import com.store.shipping.entity.ShipmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ShipmentResponse {
    private Long id;
    private Long orderId;
    private String trackingNumber;
    private String carrier;
    private ShipmentStatus status;
    private Long shippingAddressId;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
}
