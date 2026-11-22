package com.store.payment;

import com.store.payment.entity.PaymentMethod;
import com.store.payment.provider.DisabledProductionPaymentProvider;
import com.store.payment.provider.MockPaymentProvider;
import com.store.payment.provider.PaymentProvider;
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
    void productionProfileProvidesOnlyFailClosedPaymentProvider() {
        try (AnnotationConfigApplicationContext context = contextFor("prod")) {
            PaymentProvider provider = context.getBean(PaymentProvider.class);

            assertInstanceOf(DisabledProductionPaymentProvider.class, provider);
            assertEquals(1, context.getBeansOfType(PaymentProvider.class).size());

            for (PaymentMethod method : PaymentMethod.values()) {
                assertFalse(provider.supports(method), method + " must not be processed by the disabled provider");
            }

            assertThrows(UnsupportedOperationException.class,
                    () -> provider.charge(1L, new BigDecimal("10.00"),
                            PaymentMethod.CREDIT_CARD, "order-1"));
        }
    }

    @Test
    void nonProductionProfileKeepsMockProviderForDevelopmentAndTests() {
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
        context.register(DisabledProductionPaymentProvider.class, MockPaymentProvider.class);
        context.refresh();
        return context;
    }
}
