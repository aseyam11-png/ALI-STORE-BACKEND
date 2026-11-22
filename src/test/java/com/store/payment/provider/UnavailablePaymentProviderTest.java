package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnavailablePaymentProviderTest {

    @Test
    void reportsNoSupportedMethodsAndNeverExecutesACharge() {
        UnavailablePaymentProvider provider = new UnavailablePaymentProvider();

        for (PaymentMethod method : PaymentMethod.values()) {
            assertFalse(provider.supports(method));
        }

        assertThrows(UnsupportedOperationException.class,
                () -> provider.charge(42L, new BigDecimal("125.00"),
                        PaymentMethod.CREDIT_CARD, "order-42"));
    }
}
