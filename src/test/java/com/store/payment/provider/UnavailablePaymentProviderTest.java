package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnavailablePaymentProviderTest {

    @Test
    void declinesOnlineChargesWithoutInventingATransaction() {
        PaymentProvider.PaymentResult result = new UnavailablePaymentProvider().charge(
                42L, new BigDecimal("125.00"), PaymentMethod.CREDIT_CARD, "order-42");

        assertFalse(result.success());
        assertNull(result.transactionId());
        assertTrue(result.message().contains("not configured"));
    }
}
