package com.store.shipping.service;

import com.store.common.exception.BusinessException;
import com.store.shipping.dto.ShippingQuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ShippingQuoteServiceTest {

    private ShippingQuoteService service() {
        ShippingQuoteService service = new ShippingQuoteService();
        ReflectionTestUtils.setField(service, "currency", "SAR");
        ReflectionTestUtils.setField(service, "standardCost", new BigDecimal("15.00"));
        ReflectionTestUtils.setField(service, "expressCost", new BigDecimal("35.00"));
        ReflectionTestUtils.setField(service, "freeThreshold", new BigDecimal("250.00"));
        ReflectionTestUtils.setField(service, "standardMinDays", 7);
        ReflectionTestUtils.setField(service, "standardMaxDays", 15);
        ReflectionTestUtils.setField(service, "expressMinDays", 2);
        ReflectionTestUtils.setField(service, "expressMaxDays", 5);
        return service;
    }

    @Test
    void standardShipping_becomesFreeAtThreshold() {
        ShippingQuoteResponse quote = service().quote(new BigDecimal("250.00"), "STANDARD");

        assertEquals(new BigDecimal("0.00"), quote.getAmount());
        assertTrue(quote.isFree());
        assertEquals("SAR", quote.getCurrency());
        assertEquals(7, quote.getEstimatedMinDays());
        assertEquals(15, quote.getEstimatedMaxDays());
    }

    @Test
    void expressShipping_usesExpressRate() {
        ShippingQuoteResponse quote = service().quote(new BigDecimal("100.00"), "EXPRESS");

        assertEquals(new BigDecimal("35.00"), quote.getAmount());
        assertFalse(quote.isFree());
        assertEquals(2, quote.getEstimatedMinDays());
        assertEquals(5, quote.getEstimatedMaxDays());
    }

    @Test
    void unsupportedMethod_isRejected() {
        assertThrows(BusinessException.class,
                () -> service().quote(new BigDecimal("100.00"), "OVERNIGHT"));
    }
}
