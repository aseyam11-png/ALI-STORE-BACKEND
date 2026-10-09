package com.store.product.dto;

import com.store.inventory.enums.PublicAvailability;
import com.store.product.enums.ProductStatus;
import com.store.review.dto.ProductReviewSummaryResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;
    private String currency;
    private String sku;
    private ProductStatus status;
    private PublicAvailability availability;
    private Integer availableQuantity;
    private Long categoryId;
    private String categoryName;
    private String categorySlug;
    private Long brandId;
    private String brandName;
    private List<String> images;
    private String primaryImageUrl;
    private List<ProductVariantResponse> variants;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    private String promotionTitle;
    private Boolean flashSale;
    private List<com.store.shipping.dto.ShippingMethodResponse> shippingMethods;
    private BigDecimal rating;
    private long reviewCount;
    private ProductReviewSummaryResponse reviewSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}