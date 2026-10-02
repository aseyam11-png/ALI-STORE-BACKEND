package com.store.shipping.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ShippingQuoteService {
    private final BigDecimal standardCost;
    private final BigDecimal expressCost;
    private final BigDecimal freeShippingThreshold;

    public ShippingQuoteService(
            @Value("${app.shipping.standard-cost:15}") BigDecimal standardCost,
            @Value("${app.shipping.express-cost:30}") BigDecimal expressCost,
            @Value("${app.shipping.free-threshold:250}") BigDecimal freeShippingThreshold) {
        this.standardCost = standardCost.max(BigDecimal.ZERO);
        this.expressCost = expressCost.max(BigDecimal.ZERO);
        this.freeShippingThreshold = freeShippingThreshold.max(BigDecimal.ZERO);
    }

    public Quote quote(BigDecimal subtotal, String method) {
        BigDecimal safeSubtotal = subtotal == null ? BigDecimal.ZERO : subtotal.max(BigDecimal.ZERO);
        String normalized = method == null ? "STANDARD" : method.trim().toUpperCase();

        if (!normalized.equals("STANDARD") && !normalized.equals("EXPRESS")) {
            throw new IllegalArgumentException("Unsupported shipping method: " + normalized);
        }

        boolean free = normalized.equals("STANDARD")
                && safeSubtotal.compareTo(freeShippingThreshold) >= 0;
        BigDecimal amount = free
                ? BigDecimal.ZERO
                : normalized.equals("EXPRESS") ? expressCost : standardCost;

        return new Quote(
                normalized,
                amount,
                freeShippingThreshold,
                normalized.equals("EXPRESS") ? 3 : 7,
                normalized.equals("EXPRESS") ? 7 : 15
        );
    }

    public record Quote(
            String method,
            BigDecimal amount,
            BigDecimal freeShippingThreshold,
            int estimatedMinDays,
            int estimatedMaxDays
    ) {}
}
