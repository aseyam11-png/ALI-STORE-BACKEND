package com.store.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderRequest {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    // Optional. When present, the discount is always recalculated server-side from the
    // coupon's stored percentage (see OrderService) - the client only supplies the code,
    // never the discount amount itself.
    private String couponCode;
}