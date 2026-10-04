package com.store.payment.dto;

import com.store.payment.entity.PaymentCollectionStatus;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String transactionId;
    private PaymentCollectionStatus collectionStatus;
    private LocalDateTime createdAt;
}