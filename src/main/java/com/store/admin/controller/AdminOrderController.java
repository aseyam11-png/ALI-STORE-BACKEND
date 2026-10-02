package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.common.response.PageResponse;
import com.store.order.dto.OrderResponse;
import com.store.order.dto.OrderStatusUpdateRequest;
import com.store.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only order fulfillment operations - listing every customer's orders and
 * advancing order status. Status transitions are validated server-side in
 * OrderService (e.g. DELIVERED -> PENDING is rejected regardless of what is requested here).
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrdersForAdmin(pageable), "Orders retrieved successfully"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(id, request.getStatus()), "Order status updated successfully"));
    }
}
