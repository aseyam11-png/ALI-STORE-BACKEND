package com.store.shipping.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShippingQuoteServiceTest {

    private final ShippingQuoteService service = new ShippingQuoteService(
            new BigDecimal("5.00"),
            new BigDecimal("8.00"),
            new BigDecimal("100.00"),
            "7-15 business days",
            "3-7 business days"
    );

    @Test
    void standardShippingBecomesFreeAtThreshold() {
        var quote = service.quote("STANDARD", new BigDecimal("100.00"));

        assertEquals("STANDARD", quote.getMethod());
        assertEquals(new BigDecimal("0.00"), quote.getAmount());
        assertEquals("SAR", quote.getCurrency());
    }

    @Test
    void standardShippingChargesBelowThreshold() {
        var quote = service.quote("STANDARD", new BigDecimal("99.99"));

        assertEquals(new BigDecimal("5.00"), quote.getAmount());
        assertEquals("7-15 business days", quote.getEstimatedDelivery());
    }

    @Test
    void expressShippingUsesConfiguredRate() {
        var quote = service.quote("EXPRESS", new BigDecimal("20.00"));

        assertEquals(new BigDecimal("8.00"), quote.getAmount());
        assertEquals("3-7 business days", quote.getEstimatedDelivery());
    }

    @Test
    void unknownShippingMethodIsRejected() {
        assertThrows(
                RuntimeException.class,
                () -> service.quote("INVALID", new BigDecimal("20.00"))
        );
    }
}
