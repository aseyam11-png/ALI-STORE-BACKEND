package com.store.coupon.entity;

import com.store.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "expiration_date", nullable = false)
    private LocalDateTime expirationDate;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // Null = unlimited. Redemption is enforced atomically via CouponRepository.tryRedeem's
    // single UPDATE ... WHERE statement, not by reading this field and writing it back -
    // see that method's javadoc for why.
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "times_used", nullable = false)
    @Builder.Default
    private Integer timesUsed = 0;

    // Null = no minimum. Checked against the cart subtotal server-side in OrderService.
    @Column(name = "minimum_order_amount", precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount;
}