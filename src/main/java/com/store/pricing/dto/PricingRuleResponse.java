package com.store.pricing.dto;

import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class PricingRuleResponse {
    Long id;
    PricingRuleScope scope;
    PricingRuleType type;
    BigDecimal value;
    Boolean active;
    Integer priority;
    Long categoryId;
    String categoryName;
    Long productId;
    String productName;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
