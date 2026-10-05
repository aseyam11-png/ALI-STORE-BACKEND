package com.store.payment.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.OrderExpiredException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.order.entity.Order;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.order.service.OrderService;
import com.store.payment.dto.PaymentRequest;
import com.store.payment.dto.PaymentResponse;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.provider.PaymentProvider;
import com.store.payment.repository.PaymentRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentReservationService paymentReservationService;
    private final OrderService orderService;

    @Transactional(noRollbackFor = OrderExpiredException.class)
    public PaymentResponse processPayment(String email, PaymentRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + request.getOrderId()));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This order does not belong to you");
        }

        if (orderService.expireIfReservationElapsed(order)) {
            throw new OrderExpiredException("Order reservation has expired");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Payment can only be started for a pending order");
        }

        if (order.getTotalAmount().signum() == 0) {
            throw new ConflictException("No payment is required for a zero-total order");
        }

        if (order.getTotalAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessException("Payment amount does not match the order total");
        }

        if (order.getItems().stream().anyMatch(item ->
                item.getSupplier() == null
                        || item.getSupplierSku() == null
                        || item.getSupplierSku().isBlank()
                        || item.getSupplierUnitCost() == null
                        || item.getSupplierUnitCost().signum() <= 0)) {
            throw new BusinessException("Order contains an item without a valid external supplier mapping");
        }

        if (paymentRepository.findByOrderId(request.getOrderId()).isPresent()) {
            throw new ConflictException("A payment attempt already exists for this order");
        }

        if (request.getMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            Payment codPayment = paymentRepository.save(Payment.builder()
                    .orderId(request.getOrderId())
                    .amount(request.getAmount())
                    .method(PaymentMethod.CASH_ON_DELIVERY)
                    .status(PaymentStatus.PENDING)
                    .build());

            orderService.updateOrderStatus(order.getId(), OrderStatus.PROCESSING);
            return mapToResponse(codPayment);
        }

        Payment reserved = paymentReservationService.reserve(
                request.getOrderId(), request.getAmount(), request.getMethod());

        PaymentProvider.PaymentResult result =
                paymentProvider.charge(
                        order.getId(), request.getAmount(),
                        request.getMethod(), "order-" + order.getId());

        reserved.setStatus(result.success()
                ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        reserved.setTransactionId(result.transactionId());

        Payment saved = paymentRepository.save(reserved);

        if (result.success()) {
            orderService.updateOrderStatus(order.getId(), OrderStatus.PROCESSING);
        } else {
            orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
        }

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(String email, Long orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + orderId));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This order does not belong to you");
        }
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order id: " + orderId));
        return mapToResponse(payment);
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId()).orderId(payment.getOrderId()).amount(payment.getAmount())
                .method(payment.getMethod()).status(payment.getStatus())
                .transactionId(payment.getTransactionId()).createdAt(payment.getCreatedAt()).build();
    }
}
