package com.store.shipping.dto;

import com.store.shipping.entity.ShipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierShipmentUpdateRequest {

    @NotBlank(message = "Supplier code is required")
    private String supplierCode;

    @NotBlank(message = "Supplier order ID is required")
    private String supplierOrderId;

    @NotNull(message = "Shipment status is required")
    private ShipmentStatus status;

    private String trackingNumber;
    private String carrier;
    private LocalDateTime estimatedDeliveryDate;
}
