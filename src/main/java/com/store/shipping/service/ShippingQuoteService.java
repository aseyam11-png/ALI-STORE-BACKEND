package com.store.shipping.service;

import com.store.common.exception.BusinessException;
import com.store.shipping.dto.ShippingQuoteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ShippingQuoteService {

    private static final String CURRENCY = "SAR";

    private final BigDecimal standardRate;
    private final BigDecimal expressRate;
    private final BigDecimal freeShippingThreshold;
    private final String standardEstimate;
    private final String expressEstimate;

    public ShippingQuoteService(
            @Value("${app.shipping.standard-rate:0}") BigDecimal standardRate,
            @Value("${app.shipping.express-rate:8}") BigDecimal expressRate,
            @Value("${app.shipping.free-threshold:0}") BigDecimal freeShippingThreshold,
            @Value("${app.shipping.standard-estimate:7-15 business days}") String standardEstimate,
            @Value("${app.shipping.express-estimate:3-7 business days}") String expressEstimate) {
        this.standardRate = nonNegative(standardRate);
        this.expressRate = nonNegative(expressRate);
        this.freeShippingThreshold = nonNegative(freeShippingThreshold);
        this.standardEstimate = standardEstimate;
        this.expressEstimate = expressEstimate;
    }

    public ShippingQuoteResponse quote(String method, BigDecimal subtotal) {
        BigDecimal safeSubtotal = subtotal == null ? BigDecimal.ZERO : subtotal.max(BigDecimal.ZERO);
        String normalized = method == null ? "STANDARD" : method.trim().toUpperCase();

        return switch (normalized) {
            case "STANDARD" -> ShippingQuoteResponse.builder()
                    .method("STANDARD")
                    .amount(safeSubtotal.compareTo(freeShippingThreshold) >= 0 ? BigDecimal.ZERO : standardRate)
                    .currency(CURRENCY)
                    .estimatedDelivery(standardEstimate)
                    .build();
            case "EXPRESS" -> ShippingQuoteResponse.builder()
                    .method("EXPRESS")
                    .amount(expressRate)
                    .currency(CURRENCY)
                    .estimatedDelivery(expressEstimate)
                    .build();
            default -> throw new BusinessException("Unsupported shipping method: " + method);
        };
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
