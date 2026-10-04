package com.store.payment.entity;

import com.store.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "refunds", indexes = {
        @Index(name = "idx_refunds_payment_id", columnList = "payment_id"),
        @Index(name = "idx_refunds_status", columnList = "status"),
        @Index(name = "idx_refunds_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(length = 500)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "provider_refund_id", unique = true, length = 255)
    private String providerRefundId;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
