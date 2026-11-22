package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Development/test-only stand-in for a real payment gateway. Deliberately
 * excluded from the "prod" profile. Production uses
 * {@link DisabledProductionPaymentProvider}, which allows startup while
 * rejecting electronic payment methods until a real gateway is integrated.
 *
 * Does NOT implement real idempotency-key deduplication (no persistent state,
 * every supported mock call succeeds) - a real gateway must honor the supplied
 * idempotency key. Faking that behavior here would misrepresent what has
 * actually been verified, so it is intentionally left undone.
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
        return new PaymentResult(true, transactionId, "Simulated success (development/test environment only, no real gateway is configured)");
    }
}
