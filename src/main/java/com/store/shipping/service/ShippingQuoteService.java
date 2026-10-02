package com.store.shipping.service;

import com.store.common.exception.BusinessException;
import com.store.shipping.dto.ShippingQuoteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ShippingQuoteService {
    private static final String STANDARD = "STANDARD";
    private static final String EXPRESS = "EXPRESS";

    @Value("${app.shipping.currency:SAR}")
    private String currency;

    @Value("${app.shipping.standard-cost:15}")
    private BigDecimal standardCost;

    @Value("${app.shipping.express-cost:35}")
    private BigDecimal expressCost;

    @Value("${app.shipping.free-threshold:250}")
    private BigDecimal freeThreshold;

    @Value("${app.shipping.standard-min-days:7}")
    private int standardMinDays;

    @Value("${app.shipping.standard-max-days:15}")
    private int standardMaxDays;

    @Value("${app.shipping.express-min-days:2}")
    private int expressMinDays;

    @Value("${app.shipping.express-max-days:5}")
    private int expressMaxDays;

    public ShippingQuoteResponse quote(BigDecimal subtotal, String method) {
        if (subtotal == null || subtotal.signum() < 0) {
            throw new BusinessException("Subtotal must be zero or greater");
        }

        String normalized = method == null || method.isBlank()
                ? STANDARD
                : method.trim().toUpperCase();

        boolean express = EXPRESS.equals(normalized);
        if (!express && !STANDARD.equals(normalized)) {
            throw new BusinessException("Unsupported shipping method: " + method);
        }

        BigDecimal amount = !express && subtotal.compareTo(freeThreshold) >= 0
                ? BigDecimal.ZERO
                : (express ? expressCost : standardCost);

        return ShippingQuoteResponse.builder()
                .method(normalized)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .estimatedMinDays(express ? expressMinDays : standardMinDays)
                .estimatedMaxDays(express ? expressMaxDays : standardMaxDays)
                .free(amount.signum() == 0)
                .build();
    }
}