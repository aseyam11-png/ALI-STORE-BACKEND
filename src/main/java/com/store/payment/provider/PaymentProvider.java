package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * Abstraction over an actual payment gateway. The production profile uses a
 * fail-closed provider until a real gateway is configured. Non-production
 * profiles may use the mock provider for development and tests only.
 */
public interface PaymentProvider {

    /**
     * Whether this provider can execute the requested method now. PaymentService
     * checks this before reserving or reopening a payment attempt.
     */
    boolean supports(PaymentMethod method);

    /**
     * @param idempotencyKey a stable key that a real provider MUST use to
     *                       deduplicate retries for the same logical payment.
     *                       The mock provider does not implement persistent
     *                       deduplication; this guarantee remains unverified
     *                       until a real gateway is integrated.
     */
    PaymentResult charge(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey);

    default RefundResult refund(Long orderId, BigDecimal amount, String transactionId, String idempotencyKey) {
        throw new UnsupportedOperationException("Payment provider refund is not configured");
    }

    default ChargeStatusResult getChargeStatus(Long orderId, String transactionId) {
        throw new UnsupportedOperationException("Payment provider reconciliation is not configured");
    }

    record PaymentResult(boolean success, String transactionId, String message) {}
    record RefundResult(boolean success, String refundTransactionId, String message) {}
    record ChargeStatusResult(boolean completed, boolean failed, String transactionId, String message) {}
}
