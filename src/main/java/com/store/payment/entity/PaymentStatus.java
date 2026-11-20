package com.store.payment.entity;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    REQUIRES_RECONCILIATION
}
