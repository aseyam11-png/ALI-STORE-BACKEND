package com.store.b2b.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotationRequest {
    @NotNull(message = "RFQ ID is required")
    private Long rfqId;

    @NotNull(message = "Seller ID is required")
    private Long sellerId;

    @NotNull(message = "Total price is required")
    @Positive(message = "Total price must be positive")
    private BigDecimal totalPrice;

    private String terms;
}