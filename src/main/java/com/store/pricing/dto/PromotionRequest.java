package com.store.pricing.dto;

import com.store.pricing.entity.PromotionType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionRequest {
    @NotNull private Long productId;
    @NotBlank @Size(max = 160) private String title;
    @NotNull private PromotionType type;
    @NotNull @DecimalMin("0.01") private BigDecimal discountValue;
    @NotNull private LocalDateTime startsAt;
    @NotNull private LocalDateTime endsAt;
    private Boolean active = true;
    private Boolean flashSale = false;
    private Integer priority = 0;
}