package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.common.response.PageResponse;
import com.store.admin.dto.AdminOrderResponse;
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
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderResponse>>> getAllOrders(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllAdminOrderDetails(pageable), "Orders retrieved successfully"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminOrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        orderService.updateOrderStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(orderService.getAdminOrderDetails(id), "Order status updated successfully"));
    }
}
