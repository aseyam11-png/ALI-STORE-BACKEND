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

import java.math.BigDecimal;
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
        return mapToResponse(validateAndGetCoupon(code));
    }

    // Shared by getCouponByCode (customer lookup) and OrderService (checkout) so both
    // paths enforce the exact same active/expiry rule - checkout must never trust a
    // coupon a client claims is valid without re-validating it server-side.
    public Coupon validateAndGetCoupon(String code) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + code));

        if (!coupon.getIsActive() || coupon.getExpirationDate().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Coupon is expired or inactive");
        }

        return coupon;
    }

    public void checkMinimumOrder(Coupon coupon, BigDecimal orderSubtotal) {
        if (coupon.getMinimumOrderAmount() != null && orderSubtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new BusinessException("Order subtotal does not meet this coupon's minimum of " + coupon.getMinimumOrderAmount());
        }
    }

    // Must be called within the caller's own transaction (see OrderService.createOrder) so
    // that if order creation fails afterwards for any other reason, this redemption rolls
    // back with it rather than permanently consuming a use for an order that never happened.
    public void redeem(Coupon coupon) {
        int updated = couponRepository.tryRedeem(coupon.getId());
        if (updated == 0) {
            throw new BusinessException("This coupon has reached its usage limit");
        }
    }

    public CouponResponse createCoupon(CouponRequest request) {
        if (couponRepository.existsByCode(request.getCode())) {
            throw new ConflictException("Coupon code already exists");
        }

        Coupon coupon = Coupon.builder()
                .code(request.getCode().toUpperCase())
                .discountPercentage(request.getDiscountPercentage())
                .expirationDate(request.getExpirationDate())
                .isActive(true)
                .usageLimit(request.getUsageLimit())
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .timesUsed(0)
                .build();

        Coupon savedCoupon = couponRepository.save(coupon);
        return mapToResponse(savedCoupon);
    }

    public CouponResponse toggleCouponStatus(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));

        coupon.setIsActive(!coupon.getIsActive());
        Coupon updatedCoupon = couponRepository.save(coupon);
        return mapToResponse(updatedCoupon);
    }

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