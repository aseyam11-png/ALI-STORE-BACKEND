package com.store.shipping.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ShippingQuoteResponse {
    private String method;
    private BigDecimal amount;
    private BigDecimal freeShippingThreshold;
    private String currency;
    private int estimatedMinDays;
    private int estimatedMaxDays;
}
