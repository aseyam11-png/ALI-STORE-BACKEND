package com.store.shipping.entity;

import com.store.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "shipping_methods")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShippingMethod extends BaseEntity {
    @Column(nullable = false, length = 60) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 500) private String description;
    @Column(nullable = false, precision = 10, scale = 2) @Builder.Default private BigDecimal price = BigDecimal.ZERO;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "estimated_min_days", nullable = false) private Integer estimatedMinDays;
    @Column(name = "estimated_max_days", nullable = false) private Integer estimatedMaxDays;
    @Column(name = "country_code", length = 2) private String countryCode;
    @Column(nullable = false) @Builder.Default private Boolean active = true;
    @Column(nullable = false) @Builder.Default private Integer priority = 0;
}