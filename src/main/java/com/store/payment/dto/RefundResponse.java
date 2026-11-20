package com.store.payment.dto;

import com.store.payment.entity.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RefundResponse {
    private Long id;
    private Long paymentId;
    private BigDecimal amount;
    private RefundStatus status;
    private String reason;
    private String providerRefundId;
    private String lastError;
    private LocalDateTime createdAt;
}
