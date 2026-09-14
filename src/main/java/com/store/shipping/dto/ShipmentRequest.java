package com.store.shipping.dto;

import com.store.shipping.entity.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShipmentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Shipping Address ID is required")
    private Long shippingAddressId;

    private String carrier;
    private String trackingNumber;
    private ShipmentStatus status;
}