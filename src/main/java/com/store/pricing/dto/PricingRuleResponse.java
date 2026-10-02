package com.store.pricing.dto;

import com.store.pricing.entity.PricingRuleType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PricingRuleResponse {
    private Long id;
    private String name;
    private Long productId;
    private Long categoryId;
    private PricingRuleType type;
    private BigDecimal value;
    private Boolean active;
    private Integer priority;
}