package com.store.pricing.dto;

import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingRuleRequest {

    @NotNull
    private PricingRuleScope scope;

    @NotNull
    private PricingRuleType type;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal value;

    private Boolean active;
    private Integer priority;
    private Long categoryId;
    private Long productId;
}
