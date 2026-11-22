package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Development/test-only stand-in. Never active in the production profile.
 * It does not implement persistent idempotency or contact a real gateway.
 */
@Component
@Profile("!prod")
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.PAYPAL;
    }

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        String transactionId = "MOCK-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(true, transactionId,
                "Simulated success (development/test environment only; no real gateway is configured)");
    }
}
