package com.store.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FinanceSummaryResponse {
    private BigDecimal grossSales;
    private BigDecimal fulfillmentCosts;
    private BigDecimal processingFees;
    private BigDecimal adjustments;
    private BigDecimal profit;
    private BigDecimal pending;
    private BigDecimal available;
    private BigDecimal paid;
    private BigDecimal netRevenue;
}
