package com.store.payment.controller;

import com.store.common.response.ApiResponse;
import com.store.payment.dto.RefundResponse;
import com.store.payment.service.PaymentReconciliationService;
import com.store.payment.service.PaymentService;
import com.store.payment.service.RefundService;
import com.store.payment.dto.PaymentResponse;
import com.store.payment.entity.Payment;
import com.store.payment.repository.PaymentRepository;
import com.store.payment.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final RefundService refundService;
    private final PaymentRepository paymentRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<PaymentResponse> result = paymentRepository
                .findAll(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")))
                .map(this::map);
        return ResponseEntity.ok(ApiResponse.success(result, "Payments retrieved successfully"));
    }

    @PostMapping("/{paymentId}/reconcile")
    public ResponseEntity<ApiResponse<PaymentResponse>> reconcile(
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentReconciliationService.reconcile(paymentId),
                "Payment reconciliation completed"));
    }

    @PostMapping("/{paymentId}/cod/collect")
    public ResponseEntity<ApiResponse<PaymentResponse>> collectCod(
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.confirmCodCollectionByPaymentId(paymentId),
                "COD collection confirmed"));
    }

    @PostMapping("/refunds/{refundId}/retry")
    public ResponseEntity<ApiResponse<RefundResponse>> retryRefund(
            @PathVariable Long refundId) {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.retry(refundId),
                "Refund retry processed"));
    }
    private PaymentResponse map(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .collectionStatus(payment.getCollectionStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}