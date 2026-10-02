package com.store.shipping.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ShippingQuoteResponse {
    private String method;
    private BigDecimal amount;
    private String currency;
    private Integer estimatedMinDays;
    private Integer estimatedMaxDays;
    private boolean free;
}