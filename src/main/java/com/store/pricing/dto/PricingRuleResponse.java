package com.store.pricing.dto;

import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PricingRuleResponse {
    private Long id;
    private String name;
    private PricingRuleScope scope;
    private Long productId;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private PricingRuleType type;
    private BigDecimal value;
    private Integer priority;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
