package com.store.payment;

import com.store.payment.entity.PaymentMethod;
import com.store.payment.provider.MockPaymentProvider;
import com.store.payment.provider.PaymentProvider;
import com.store.payment.provider.UnavailablePaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionPaymentProviderProfileTest {

    @Test
    void productionProfileSelectsOnlyFailClosedProvider() {
        try (AnnotationConfigApplicationContext context = contextFor("prod")) {
            PaymentProvider provider = context.getBean(PaymentProvider.class);
            assertInstanceOf(UnavailablePaymentProvider.class, provider);
            assertEquals(1, context.getBeansOfType(PaymentProvider.class).size());
            for (PaymentMethod method : PaymentMethod.values()) {
                assertFalse(provider.supports(method));
            }
            assertThrows(UnsupportedOperationException.class,
                    () -> provider.charge(1L, new BigDecimal("10.00"),
                            PaymentMethod.CREDIT_CARD, "order-1"));
        }
    }

    @Test
    void nonProductionProfileSelectsMockProvider() {
        try (AnnotationConfigApplicationContext context = contextFor("test")) {
            PaymentProvider provider = context.getBean(PaymentProvider.class);
            assertInstanceOf(MockPaymentProvider.class, provider);
            assertEquals(1, context.getBeansOfType(PaymentProvider.class).size());
            assertTrue(provider.supports(PaymentMethod.CREDIT_CARD));
            assertTrue(provider.supports(PaymentMethod.PAYPAL));
            assertFalse(provider.supports(PaymentMethod.CASH_ON_DELIVERY));
        }
    }

    private AnnotationConfigApplicationContext contextFor(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.register(UnavailablePaymentProvider.class, MockPaymentProvider.class);
        context.refresh();
        return context;
    }
}
