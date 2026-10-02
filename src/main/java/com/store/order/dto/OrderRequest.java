package com.store.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderRequest {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    // Optional for backward compatibility. Server defaults to STANDARD.
    private String shippingMethod;

    // Optional. The server validates and calculates the discount from the stored coupon.
    private String couponCode;
}
