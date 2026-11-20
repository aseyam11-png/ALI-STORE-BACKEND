package com.store.shipping.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShipmentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    private Long shippingAddressId;
    private String shippingAddressSnapshot;
    private String carrier;
    private String trackingNumber;
}
