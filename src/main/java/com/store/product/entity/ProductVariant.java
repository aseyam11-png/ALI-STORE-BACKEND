package com.store.product.entity;

import com.store.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 120)
    private String sku;

    @Column(name = "option_name", nullable = false, length = 80)
    private String optionName;

    @Column(name = "option_value", nullable = false, length = 160)
    private String optionValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "supplier_sku", length = 120)
    private String supplierSku;

    @Column(name = "supplier_cost", precision = 10, scale = 2)
    private BigDecimal supplierCost;

    @Column(name = "available_quantity", nullable = false)
    @Builder.Default
    private Integer availableQuantity = 0;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}