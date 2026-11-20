package com.store.pricing.entity;

import com.store.category.entity.Category;
import com.store.common.entity.BaseEntity;
import com.store.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pricing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PricingRuleScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PricingRuleType type;

    @Column(name = "rule_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal value;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}
