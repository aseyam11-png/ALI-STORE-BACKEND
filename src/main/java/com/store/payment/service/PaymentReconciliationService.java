package com.store.payment.service;

import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.order.entity.Order;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.payment.dto.PaymentResponse;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentCollectionStatus;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.provider.PaymentProvider;
import com.store.payment.repository.PaymentRepository;
import com.store.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProvider paymentProvider;
    private final com.store.order.service.OrderService orderService;
    private final RefundService refundService;

    @Transactional
    public PaymentResponse reconcile(Long paymentId) {
        // Use the same lock order as order cancellation (order row, then payment row)
        // so expiry/cancellation and provider reconciliation serialize deterministically.
        Payment paymentSnapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        Order order = orderRepository.findByIdForUpdate(paymentSnapshot.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!payment.getOrderId().equals(order.getId())) {
            throw new ConflictException("Payment order reference changed during reconciliation");
        }

        if (payment.getStatus() != PaymentStatus.REQUIRES_RECONCILIATION
                && payment.getStatus() != PaymentStatus.PENDING) {
            return map(payment);
        }

        // The reservation may have elapsed before the scheduled expiry worker has
        // persisted CANCELLED. Resolve that state while holding the order lock, before
        // contacting the provider, so a late successful charge is routed to refund.
        if (order.getStatus() == OrderStatus.PENDING) {
            orderService.expireIfReservationElapsed(order);
        }

        try {
            PaymentProvider.ChargeStatusResult result =
                    paymentProvider.getChargeStatus(payment.getOrderId(), payment.getTransactionId());

            if (result != null && result.completed()) {
                if (result.transactionId() == null || result.transactionId().isBlank()) {
                    payment.setStatus(PaymentStatus.REQUIRES_RECONCILIATION);
                    payment.setProviderError(
                            result.message() == null || result.message().isBlank()
                                    ? "Provider reported a completed charge without a transaction id"
                                    : result.message());
                    paymentRepository.save(payment);
                    return map(payment);
                }

                payment.setTransactionId(result.transactionId());
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCollectionStatus(PaymentCollectionStatus.NOT_REQUIRED);
                payment.setProviderError(null);
                paymentRepository.save(payment);

                if (order.getStatus() == OrderStatus.PENDING) {
                    orderService.updateOrderStatus(order.getId(), OrderStatus.PROCESSING);
                } else if (order.getStatus() == OrderStatus.CANCELLED
                        && payment.getMethod() != PaymentMethod.CASH_ON_DELIVERY) {
                    // Do not leave captured funds attached to a cancelled order.
                    refundService.prepareOrderCancellationRefund(
                            order.getId(), "Late provider confirmation after order cancellation");
                }
                return map(payment);
            }

            if (result != null && result.failed()) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setProviderError(result.message());
                paymentRepository.save(payment);

                if (order.getStatus() == OrderStatus.PENDING) {
                    orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
                }
                return map(payment);
            }

            payment.setStatus(PaymentStatus.REQUIRES_RECONCILIATION);
            payment.setProviderError(result == null || result.message() == null
                    ? "Provider did not return a definitive charge status"
                    : result.message());
            paymentRepository.save(payment);
            return map(payment);
        } catch (UnsupportedOperationException ex) {
            throw new ConflictException("Payment provider reconciliation is not configured");
        } catch (RuntimeException ex) {
            throw new ConflictException(
                    ex.getMessage() == null || ex.getMessage().isBlank()
                            ? "Payment provider reconciliation failed"
                            : ex.getMessage());
        }
    }

    private PaymentResponse map(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .collectionStatus(payment.getCollectionStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
