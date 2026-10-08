package com.store.pricing.dto;

import com.store.pricing.entity.PromotionType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class PromotionResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private String title;
    private PromotionType type;
    private BigDecimal discountValue;
    private BigDecimal originalPrice;
    private BigDecimal price;
    private BigDecimal discountPercentage;
    private String currency;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean active;
    private Boolean flashSale;
    private Integer priority;
}