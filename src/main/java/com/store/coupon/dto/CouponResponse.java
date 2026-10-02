package com.store.coupon.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CouponResponse {
    private Long id;
    private String code;
    private BigDecimal discountPercentage;
    private LocalDateTime expirationDate;
    private Boolean isActive;
    private Integer usageLimit;
    private Integer timesUsed;
    private BigDecimal minimumOrderAmount;
}