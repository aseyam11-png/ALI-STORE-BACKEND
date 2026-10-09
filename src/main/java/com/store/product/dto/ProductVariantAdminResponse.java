package com.store.product.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductVariantAdminResponse {
    private Long id;
    private Long productId;
    private String sku;
    private String supplierSku;
    private BigDecimal supplierCost;
    private String name;
    private String value;
    private BigDecimal price;
    private String currency;
    private Integer availableQuantity;
    private String imageUrl;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
