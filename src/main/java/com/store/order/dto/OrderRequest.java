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

    // Optional while no shipping methods are configured. When active methods exist,
    // checkout must send the selected code and the server recalculates the amount.
    private String shippingMethodCode;
    private String shippingCountry;
}
