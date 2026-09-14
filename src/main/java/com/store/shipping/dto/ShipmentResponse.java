package com.store.shipping.dto;

import com.store.shipping.entity.ShipmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ShipmentResponse {

    private Long id;
    private Long orderId;
    private String trackingNumber;
    private String carrier;
    private ShipmentStatus status;
    private Long shippingAddressId; // تم الاعتماد على ID العنوان بدلاً من الكائن ليتوافق مع التحويل
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
}