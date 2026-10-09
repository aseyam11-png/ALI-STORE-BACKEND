package com.store.cart.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String variantName;
    private String variantValue;
    private String variantSku;
    private String productName;
    private BigDecimal productPrice;
    private String currency;
    private Integer quantity;
    private BigDecimal subTotal;
}