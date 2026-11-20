package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Development/test-only stand-in for a real payment gateway. Deliberately
 * excluded from the "prod" profile: a production deployment with no real
 * {@link PaymentProvider} bean configured will fail to start rather than
 * silently accept fake payments.
 *
 * Does NOT implement real idempotency-key deduplication (no persistent state,
 * every call "succeeds") - a real gateway integration must actually honor
 * idempotencyKey to get the crash-then-retry double-charge guarantee described
 * on {@link PaymentProvider#charge}. Faking that behavior here would misrepresent
 * what has actually been verified, so it is intentionally left undone.
 */
@Component
@Profile("!prod")
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        String transactionId = "MOCK-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(true, transactionId, "Simulated success (development/test environment only, no real gateway is configured)");
    }
}
