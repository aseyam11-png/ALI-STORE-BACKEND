package com.store.order.dto;

import com.store.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal shippingAmount;
    private String shippingMethod;
    private String couponCode;
    private BigDecimal discountAmount;
    private String paymentStatus;
    private String paymentMethod;
    private String shippingAddress;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;
    private List<ShipmentSummaryResponse> shipments;
}
