package com.store.fulfillment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierFulfillmentResponse {
    private Boolean accepted;
    private String supplierOrderId;
    private String carrier;
    private String trackingNumber;
    private LocalDateTime estimatedDeliveryDate;
}
