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
 * point leaves a durable PENDING row behind. A retry then hits the existing
 * findByOrderId pre-check / the unique constraint on payments.order_id and is
 * rejected with 409 - our system will never attempt a second provider charge for
 * that order again. The tradeoff (documented, not solved here): if the original
 * charge actually succeeded at the provider before the crash, the order is left
 * stuck showing PENDING until someone reconciles it against the provider's
 * records (e.g. a webhook handler or an admin reconciliation job) - neither of
 * which exists in this codebase. That is real, undone technical debt, not
 * something this change claims to fix.
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
}
