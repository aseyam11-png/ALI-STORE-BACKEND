package com.store.payment.service;

import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.repository.PaymentRepository;
import com.store.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commits the PENDING payment reservation in its OWN transaction (REQUIRES_NEW),
 * independent of PaymentService.processPayment's outer transaction.
 *
 * Why this exists: without it, the reservation insert and the post-charge status
 * update live in the same transaction. If the process crashes after
 * PaymentProvider.charge(...) succeeds but before that transaction commits, the
 * WHOLE transaction - including the reservation - is lost. A client retry would
 * then find no payment row at all and reserve+charge again, potentially
 * double-charging a real gateway even though the DB stayed "clean" after the crash.
 *
 * With the reservation committed separately and immediately, a crash after that
 * point leaves a durable PENDING row behind. A retry never blindly recharges a
 * PENDING or unknown-outcome payment. A definitively FAILED payment may be reopened
 * in its existing row with the same idempotency key; REQUIRES_RECONCILIATION remains
 * blocked until an authoritative provider result exists.
 */
@Service
@RequiredArgsConstructor
public class PaymentReservationService {

    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment reserve(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        return paymentRepository.saveAndFlush(Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .method(method)
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .collectionStatus(com.store.payment.entity.PaymentCollectionStatus.NOT_REQUIRED)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long paymentId, String message) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setProviderError(message == null || message.isBlank()
                ? "Payment provider reported failure"
                : message.substring(0, Math.min(message.length(), 1000)));
        paymentRepository.save(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRequiresReconciliation(Long paymentId, String message) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for reconciliation"));
        payment.setStatus(PaymentStatus.REQUIRES_RECONCILIATION);
        payment.setProviderError(message == null || message.isBlank()
                ? "Provider outcome could not be confirmed"
                : message.substring(0, Math.min(message.length(), 1000)));
        paymentRepository.save(payment);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment reopenFailed(Long paymentId, BigDecimal amount, PaymentMethod method) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new com.store.common.exception.ConflictException(
                    "Only failed payments can be retried");
        }
        if (payment.getAmount().compareTo(amount) != 0 || payment.getMethod() != method) {
            throw new com.store.common.exception.ConflictException(
                    "Payment retry does not match the original payment attempt");
        }

        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId(null);
        payment.setProviderError(null);
        payment.setCollectionStatus(
                com.store.payment.entity.PaymentCollectionStatus.NOT_REQUIRED);
        payment.setIdempotencyKey(
                "order-" + payment.getOrderId() + ":retry-" + UUID.randomUUID());
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment markCompleted(Long paymentId, String transactionId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new com.store.common.exception.ConflictException(
                    "Only pending payments can be completed");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new com.store.common.exception.ConflictException(
                    "Provider did not return a transaction identifier");
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId(transactionId.trim());
        payment.setProviderError(null);
        payment.setCollectionStatus(
                com.store.payment.entity.PaymentCollectionStatus.NOT_REQUIRED);
        return paymentRepository.saveAndFlush(payment);
    }



}
