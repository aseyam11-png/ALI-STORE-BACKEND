package com.store.shipping.dto;

import com.store.shipping.entity.ShipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierShipmentUpdateRequest {

    @NotBlank(message = "Supplier code is required")
    @Size(max = 80, message = "Supplier code exceeds the supported length")
    private String supplierCode;

    @NotBlank(message = "Supplier order ID is required")
    @Size(max = 255, message = "Supplier order ID exceeds the supported length")
    private String supplierOrderId;

    @NotNull(message = "Shipment status is required")
    private ShipmentStatus status;

    @Size(max = 100, message = "Tracking number exceeds the supported length")
    private String trackingNumber;

    @Size(max = 100, message = "Carrier exceeds the supported length")
    private String carrier;

    private LocalDateTime estimatedDeliveryDate;
}
