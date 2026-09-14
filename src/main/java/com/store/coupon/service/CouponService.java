package com.store.coupon.service;

import com.store.coupon.dto.CouponRequest;
import com.store.coupon.dto.CouponResponse;
import com.store.coupon.entity.Coupon;
import com.store.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    public List<CouponResponse> getAllCoupons() {
        return couponRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CouponResponse getCouponByCode(String code) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Coupon not found: " + code));

        if (!coupon.getIsActive() || coupon.getExpirationDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Coupon is expired or inactive");
        }

        return mapToResponse(coupon);
    }

    public CouponResponse createCoupon(CouponRequest request) {
        if (couponRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Coupon code already exists");
        }

        Coupon coupon = Coupon.builder()
                .code(request.getCode().toUpperCase())
                .discountPercentage(request.getDiscountPercentage())
                .expirationDate(request.getExpirationDate())
                .isActive(true)
                .build();

        Coupon savedCoupon = couponRepository.save(coupon);
        return mapToResponse(savedCoupon);
    }

    public CouponResponse toggleCouponStatus(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found with id: " + id));

        coupon.setIsActive(!coupon.getIsActive());
        Coupon updatedCoupon = couponRepository.save(coupon);
        return mapToResponse(updatedCoupon);
    }

    public void deleteCoupon(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new RuntimeException("Coupon not found with id: " + id);
        }
        couponRepository.deleteById(id);
    }

    private CouponResponse mapToResponse(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountPercentage(coupon.getDiscountPercentage())
                .expirationDate(coupon.getExpirationDate())
                .isActive(coupon.getIsActive())
                .build();
    }
}