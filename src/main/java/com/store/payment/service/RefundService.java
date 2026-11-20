package com.store.payment.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.order.entity.Order;
import com.store.order.entity.OrderItem;
import com.store.order.repository.OrderRepository;
import com.store.payment.dto.RefundResponse;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.entity.Refund;
import com.store.payment.entity.RefundStatus;
import com.store.payment.provider.PaymentProvider;
import com.store.payment.repository.PaymentRepository;
import com.store.payment.repository.RefundRepository;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final PaymentProvider paymentProvider;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public RefundResponse prepareOrderCancellationRefund(Long orderId, String reason) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .flatMap(p -> paymentRepository.findByIdForUpdate(p.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order id: " + orderId));

        if (payment.getMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            return null;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return latestRefund(payment.getId());
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            throw new BusinessException("A completed payment is required before creating a refund");
        }

        BigDecimal refundable = remainingRefundable(payment);
        if (refundable.signum() <= 0) return latestRefund(payment.getId());

        return prepareRefund(payment, refundable, reason, "order:" + orderId + ":cancellation");
    }

    @Transactional
    public RefundResponse prepareCancelledShipmentRefund(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for id: " + shipmentId));

        if (shipment.getStatus() != ShipmentStatus.CANCELLED
                || shipment.getSupplier() == null) {
            return null;
        }

        Order order = orderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for id: " + shipment.getOrderId()));
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .flatMap(p -> paymentRepository.findByIdForUpdate(p.getId()))
                .orElse(null);

        if (payment == null
                || payment.getMethod() == PaymentMethod.CASH_ON_DELIVERY
                || payment.getStatus() != PaymentStatus.COMPLETED) {
            return null;
        }

        BigDecimal orderSubtotal = order.getItems().stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (orderSubtotal.signum() <= 0) return null;

        BigDecimal failedPartitionSubtotal = order.getItems().stream()
                .filter(item -> item.getSupplier() != null
                        && shipment.getSupplier().getId().equals(item.getSupplier().getId()))
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (failedPartitionSubtotal.signum() <= 0) return null;

        BigDecimal refundAmount = payment.getAmount()
                .multiply(failedPartitionSubtotal)
                .divide(orderSubtotal, 2, RoundingMode.HALF_UP);

        BigDecimal refundable = remainingRefundable(payment);
        refundAmount = refundAmount.min(refundable);
        if (refundAmount.signum() <= 0) return null;

        return prepareRefund(
                payment,
                refundAmount,
                "Supplier fulfillment failed for shipment " + shipmentId,
                "order:" + order.getId() + ":shipment:" + shipmentId + ":fulfillment-failure");
    }

    @Transactional
    public RefundResponse retry(Long refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));

        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            return map(refund);
        }

        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for refund"));

        if (refund.getStatus() == RefundStatus.FAILED
                || refund.getStatus() == RefundStatus.REQUIRES_RECONCILIATION) {
            refund.setStatus(RefundStatus.PENDING);
            refund.setLastError(null);
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            paymentRepository.save(payment);
            refundRepository.save(refund);
        }

        process(refund, payment);
        return map(refundRepository.findById(refundId).orElseThrow());
    }

    @Scheduled(fixedDelayString = "${app.payment.refund-poll-ms:30000}")
    public void processPendingRefunds() {
        List<Long> refundIds = refundRepository.findByStatusOrderByCreatedAtAsc(RefundStatus.PENDING)
                .stream()
                .limit(20)
                .map(Refund::getId)
                .toList();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        refundIds.forEach(id -> tx.executeWithoutResult(status -> processPendingRefund(id)));
    }

    @Transactional
    private void processPendingRefund(Long refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId).orElse(null);
        if (refund == null || refund.getStatus() != RefundStatus.PENDING) return;

        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId()).orElse(null);
        if (payment != null) process(refund, payment);
    }

    private RefundResponse prepareRefund(
            Payment payment,
            BigDecimal amount,
            String reason,
            String idempotencyKey) {
        return refundRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::map)
                .orElseGet(() -> {
                    BigDecimal refundable = remainingRefundable(payment);
                    if (amount.signum() <= 0 || amount.compareTo(refundable) > 0) {
                        throw new BusinessException("Refund amount exceeds the remaining refundable payment amount");
                    }

                    Refund refund = Refund.builder()
                            .paymentId(payment.getId())
                            .amount(amount)
                            .status(RefundStatus.PENDING)
                            .reason(reason)
                            .idempotencyKey(idempotencyKey)
                            .build();

                    payment.setStatus(PaymentStatus.REFUND_PENDING);
                    paymentRepository.save(payment);

                    return map(refundRepository.save(refund));
                });
    }

    private void process(Refund refund, Payment payment) {
        if (refund.getStatus() != RefundStatus.PENDING) return;

        try {
            if (payment.getMethod() == PaymentMethod.CASH_ON_DELIVERY) {
                refund.setStatus(RefundStatus.FAILED);
                refund.setLastError("COD was not collected through a payment gateway");
                refundRepository.save(refund);
                return;
            }

            if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
                refund.setStatus(RefundStatus.REQUIRES_RECONCILIATION);
                refund.setLastError("Completed payment has no provider transaction id");
                refundRepository.save(refund);
                return;
            }

            PaymentProvider.RefundResult result = paymentProvider.refund(
                    payment.getOrderId(),
                    refund.getAmount(),
                    payment.getTransactionId(),
                    refund.getIdempotencyKey());

            if (result != null && result.success()
                    && result.refundTransactionId() != null
                    && !result.refundTransactionId().isBlank()) {
                refund.setStatus(RefundStatus.SUCCEEDED);
                refund.setProviderRefundId(result.refundTransactionId());
                refund.setLastError(null);
            } else {
                refund.setStatus(RefundStatus.REQUIRES_RECONCILIATION);
                refund.setLastError(result == null ? "Payment provider returned no refund result"
                        : result.message());
            }
        } catch (UnsupportedOperationException ex) {
            refund.setStatus(RefundStatus.REQUIRES_RECONCILIATION);
            refund.setLastError("Payment provider does not support refunds");
        } catch (RuntimeException ex) {
            refund.setStatus(RefundStatus.REQUIRES_RECONCILIATION);
            String message = ex.getMessage();
            refund.setLastError(message == null || message.isBlank()
                    ? ex.getClass().getSimpleName()
                    : message.substring(0, Math.min(message.length(), 1000)));
        }

        refundRepository.save(refund);

        BigDecimal successful = refundRepository.sumByPaymentIdAndStatus(
                payment.getId(), RefundStatus.SUCCEEDED);
        payment.setStatus(successful.compareTo(payment.getAmount()) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.REFUND_PENDING);
        paymentRepository.save(payment);
    }

    private BigDecimal remainingRefundable(Payment payment) {
        BigDecimal refunded = refundRepository.sumByPaymentIdAndStatus(
                payment.getId(), RefundStatus.SUCCEEDED);
        BigDecimal pending = refundRepository.sumByPaymentIdAndStatus(
                payment.getId(), RefundStatus.PENDING);
        BigDecimal unresolved = refundRepository.sumByPaymentIdAndStatus(
                payment.getId(), RefundStatus.REQUIRES_RECONCILIATION);
        return payment.getAmount()
                .subtract(refunded)
                .subtract(pending)
                .subtract(unresolved)
                .max(BigDecimal.ZERO);
    }

    private RefundResponse latestRefund(Long paymentId) {
        return refundRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .reduce((first, second) -> second)
                .map(this::map)
                .orElse(null);
    }

    private RefundResponse map(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(refund.getPaymentId())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .reason(refund.getReason())
                .providerRefundId(refund.getProviderRefundId())
                .lastError(refund.getLastError())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}
