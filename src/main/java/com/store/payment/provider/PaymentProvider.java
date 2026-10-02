package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * Abstraction over an actual payment gateway. No real gateway is integrated
 * in this phase (explicitly deferred) - this interface exists so that
 * charging money is never hardcoded to "always succeeds" again. Production
 * deployments must supply a real implementation as a Spring bean; if none is
 * configured, the application fails to start rather than silently faking
 * successful payments (see {@link MockPaymentProvider}, which is only active
 * outside the "prod" profile).
 */
public interface PaymentProvider {

    /**
     * @param idempotencyKey a stable, deterministic key (currently "order-{orderId}" - see
     *                       PaymentService) that a real provider implementation MUST use to
     *                       deduplicate retried charge attempts for the same logical payment
     *                       (e.g. Stripe's Idempotency-Key header, or an equivalent mechanism
     *                       on whatever gateway is integrated). This interface only defines the
     *                       contract; MockPaymentProvider does NOT implement real deduplication
     *                       (it is a mock with no persistent state), so end-to-end "provider
     *                       never double-charges after a crash-then-retry" cannot be verified
     *                       against it - that guarantee depends entirely on the concrete
     *                       provider implementation actually honoring this key. Documented as a
     *                       known gap until a real gateway is integrated (see PaymentService).
     */
    PaymentResult charge(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey);

    record PaymentResult(boolean success, String transactionId, String message) {
    }
}
