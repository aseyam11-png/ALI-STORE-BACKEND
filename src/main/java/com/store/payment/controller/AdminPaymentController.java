package com.store.payment.controller;

import com.store.common.response.ApiResponse;
import com.store.common.response.PageResponse;
import com.store.payment.dto.PaymentResponse;
import com.store.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        paymentService.getAllForAdmin(pageable),
                        "Payments retrieved successfully"));
    }
}
