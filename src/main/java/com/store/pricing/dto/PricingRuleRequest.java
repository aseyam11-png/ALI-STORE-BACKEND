package com.store.pricing.dto;

import com.store.pricing.entity.PricingRule.RuleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingRuleRequest {
    @NotBlank
    private String name;

    private Long productId;
    private Long categoryId;

    @NotNull
    private RuleType type;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal value;

    private Boolean active;
    private Integer priority;
}
