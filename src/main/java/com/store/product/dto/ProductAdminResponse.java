package com.store.product.dto;

import com.store.product.enums.ProductStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductAdminResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;
    private String currency;
    private String sku;
    private ProductStatus status;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private List<String> images;
    private String primaryImageUrl;

    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private String supplierSku;
    private BigDecimal supplierCost;
}
