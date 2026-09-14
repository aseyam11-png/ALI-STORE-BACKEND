package com.store.payment.service;

import com.store.payment.dto.PaymentRequest;
import com.store.payment.dto.PaymentResponse;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        paymentRepository.findByOrderId(request.getOrderId()).ifPresent(p -> {
            throw new RuntimeException("Payment already exists for order id: " + request.getOrderId());
        });

        // محاكاة لإنشاء معرّف معتملية (Transaction ID) فريد
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .method(request.getMethod())
                .status(PaymentStatus.COMPLETED) // محاكاة لنجاح العملية مباشرة
                .transactionId(transactionId)
                .build();

        Payment saved = paymentRepository.save(payment);
        return mapToResponse(saved);
    }

    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order id: " + orderId));
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