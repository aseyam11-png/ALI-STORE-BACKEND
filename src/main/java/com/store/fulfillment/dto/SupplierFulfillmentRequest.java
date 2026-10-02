package com.store.fulfillment.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SupplierFulfillmentRequest {
    Long orderId;
    Long shipmentId;
    String idempotencyKey;
    String supplierCode;
    String shippingAddress;
    List<Item> items;

    @Value
    @Builder
    public static class Item {
        String supplierSku;
        Integer quantity;

    }
}
