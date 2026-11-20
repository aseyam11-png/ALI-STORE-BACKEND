package com.store.fulfillment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FulfillmentSupplierAcceptedRequest(
        @NotBlank @Size(max = 255) String supplierOrderId,
        @Size(max = 100) String carrier,
        @Size(max = 100) String trackingNumber) {
}
