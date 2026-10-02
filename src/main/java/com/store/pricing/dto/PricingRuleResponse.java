package com.store.pricing.dto;

import com.store.pricing.entity.PricingRule.RuleType;
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
    private RuleType type;
    private BigDecimal value;
    private boolean active;
    private int priority;
}
