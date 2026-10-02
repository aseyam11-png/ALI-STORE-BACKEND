package com.store.payment.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.payment.dto.PaymentRequest;
import com.store.payment.dto.PaymentResponse;
import com.store.payment.entity.Payment;
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

    @Transactional
    public PaymentResponse processPayment(String email, PaymentRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This order does not belong to you");
        }

        if (order.getTotalAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessException("Payment amount does not match the order total");
        }

        paymentRepository.findByOrderId(request.getOrderId()).ifPresent(p -> {
            throw new ConflictException("Payment already exists for order id: " + request.getOrderId());
        });

        // Reserve the payment slot in its OWN transaction (REQUIRES_NEW, commits
        // immediately) BEFORE calling the provider - see PaymentReservationService for why.
        // payments.order_id is unique (V3 migration), so if two concurrent requests both
        // pass the pre-check above, only one of these reservations can succeed - the loser
        // fails here with a DataIntegrityViolationException (-> clean 409, see
        // GlobalExceptionHandler) and never reaches paymentProvider.charge(...).
        Payment reserved = paymentReservationService.reserve(request.getOrderId(), request.getAmount(), request.getMethod());

        // Deterministic per-order key. A real provider implementation must use this to
        // deduplicate retried charges (see PaymentProvider#charge's javadoc for the
        // documented limitation: MockPaymentProvider does not actually honor it).
        String idempotencyKey = "order-" + order.getId();
        PaymentProvider.PaymentResult result = paymentProvider.charge(order.getId(), request.getAmount(), request.getMethod(), idempotencyKey);

        reserved.setStatus(result.success() ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        reserved.setTransactionId(result.transactionId());

        Payment saved = paymentRepository.save(reserved);
        return mapToResponse(saved);
    }

    public PaymentResponse getPaymentByOrderId(String email, Long orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This order does not belong to you");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order id: " + orderId));
        return mapToResponse(payment);
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
