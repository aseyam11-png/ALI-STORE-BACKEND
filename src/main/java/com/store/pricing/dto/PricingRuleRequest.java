package com.store.pricing.dto;

import com.store.pricing.entity.PricingRuleType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingRuleRequest {
    @NotBlank
    private String name;

    private Long productId;
    private Long categoryId;

    @NotNull
    private PricingRuleType type;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal value;

    @NotNull
    private Boolean active;

    @NotNull
    @Min(0)
    private Integer priority;
}