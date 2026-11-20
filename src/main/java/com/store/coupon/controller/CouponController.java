package com.store.coupon.controller;

import com.store.common.response.ApiResponse;
import com.store.coupon.dto.CouponRequest;
import com.store.coupon.dto.CouponResponse;
import com.store.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getAllCoupons() {
        return ResponseEntity.ok(ApiResponse.success(couponService.getAllCoupons(), "Coupons retrieved successfully"));
    }

    @GetMapping("/validate/{code}")
    public ResponseEntity<ApiResponse<CouponResponse>> validateCoupon(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(couponService.getCouponByCode(code), "Coupon is valid"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(@Valid @RequestBody CouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(couponService.createCoupon(request), "Coupon created successfully"));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponse>> toggleCouponStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponService.toggleCouponStatus(id), "Coupon status updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Coupon deleted successfully"));
    }
}