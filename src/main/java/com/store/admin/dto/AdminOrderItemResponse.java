package com.store.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Product details displayed to store administrators in the order operations screen.
 * Supplier identifiers and supplier costs are intentionally excluded.
 */
@Data
@Builder
public class AdminOrderItemResponse {
    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subTotal;
}
