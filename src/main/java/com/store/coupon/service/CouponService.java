package com.store.coupon.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.coupon.dto.CouponRequest;
import com.store.coupon.dto.CouponResponse;
import com.store.coupon.entity.Coupon;
import com.store.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    public List<CouponResponse> getAllCoupons() {
        return couponRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public CouponResponse getCouponByCode(String code) {
        return mapToResponse(validateAndGetCoupon(code));
    }

    public Coupon validateAndGetCoupon(String code) {
        String normalizedCode = code == null ? null : code.trim().toUpperCase();
        if (normalizedCode == null || normalizedCode.isBlank()) {
            throw new BusinessException("Coupon code is required");
        }

        Coupon coupon = couponRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + normalizedCode));

        if (!Boolean.TRUE.equals(coupon.getIsActive())
                || !coupon.getExpirationDate().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Coupon is expired or inactive");
        }

        return coupon;
    }

    public void checkMinimumOrder(Coupon coupon, BigDecimal orderSubtotal) {
        if (coupon.getMinimumOrderAmount() != null
                && orderSubtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new BusinessException(
                    "Order subtotal does not meet this coupon's minimum of " + coupon.getMinimumOrderAmount());
        }
    }

    public void redeem(Coupon coupon) {
        if (couponRepository.tryRedeem(coupon.getId()) == 0) {
            throw new BusinessException("This coupon has reached its usage limit");
        }
    }

    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.existsByCode(code)) {
            throw new ConflictException("Coupon code already exists");
        }

        Coupon coupon = Coupon.builder()
                .code(code)
                .discountPercentage(request.getDiscountPercentage())
                .expirationDate(request.getExpirationDate())
                .isActive(true)
                .usageLimit(request.getUsageLimit())
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .timesUsed(0)
                .build();

        return mapToResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse toggleCouponStatus(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));
        coupon.setIsActive(!Boolean.TRUE.equals(coupon.getIsActive()));
        return mapToResponse(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteCoupon(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new ResourceNotFoundException("Coupon not found with id: " + id);
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
                .usageLimit(coupon.getUsageLimit())
                .timesUsed(coupon.getTimesUsed())
                .minimumOrderAmount(coupon.getMinimumOrderAmount())
                .build();
    }
}
