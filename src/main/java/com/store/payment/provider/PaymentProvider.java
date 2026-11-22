package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * Abstraction over an actual payment gateway. No real gateway is integrated
 * yet. The production profile uses {@link DisabledProductionPaymentProvider}
 * until a real provider is configured; it reports electronic methods as
 * unsupported so the application can start without accepting simulated
 * payments. {@link MockPaymentProvider} remains restricted to non-production
 * profiles.
 */
public interface PaymentProvider {

    /**
     * Whether this provider can execute a payment method right now.
     *
     * <p>PaymentService checks this before reserving a payment attempt. A provider
     * that cannot handle a method must return false so the order is left unchanged
     * and no payment record is created.</p>
     */
    boolean supports(PaymentMethod method);

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

    /**
     * A concrete production gateway must implement this operation. Returning
     * "refunded" without provider confirmation is forbidden by contract.
     */
    default RefundResult refund(
            Long orderId,
            BigDecimal amount,
            String transactionId,
            String idempotencyKey) {
        throw new UnsupportedOperationException("Payment provider refund is not configured");
    }

    /**
     * Used only when the original charge outcome is unknown after a transport
     * or process failure. A real provider should query its authoritative
     * transaction state.
     */
    default ChargeStatusResult getChargeStatus(Long orderId, String transactionId) {
        throw new UnsupportedOperationException("Payment provider reconciliation is not configured");
    }

    record PaymentResult(boolean success, String transactionId, String message) {
    }

    record RefundResult(boolean success, String refundTransactionId, String message) {
    }

    record ChargeStatusResult(boolean completed, boolean failed, String transactionId, String message) {
    }
}
