package com.store.admin.dto;

import com.store.order.dto.ShipmentSummaryResponse;
import com.store.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-only view of an order. Customer contact data is kept out of the shared
 * OrderResponse used by customer-facing endpoints.
 */
@Data
@Builder
public class AdminOrderResponse {
    private Long id;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String currency;
    private String couponCode;
    private BigDecimal discountAmount;
    private String shippingAddress;
    private LocalDateTime createdAt;
    private List<AdminOrderItemResponse> items;
    private List<ShipmentSummaryResponse> shipments;
}
