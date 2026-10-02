package com.store.pricing.dto;

import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingRuleRequest {

    @NotBlank
    private String name;

    @NotNull
    private PricingRuleScope scope;

    private Long productId;
    private Long categoryId;

    @NotNull
    private PricingRuleType type;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal value;

    @NotNull
    private Integer priority;

    private Boolean active;
}
