package com.store.order.dto;

import com.store.shipping.entity.ShipmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ShipmentSummaryResponse {
    private Long id;
    private ShipmentStatus status;
    private String carrier;
    private String trackingNumber;
    private LocalDateTime estimatedDeliveryDate;
}
