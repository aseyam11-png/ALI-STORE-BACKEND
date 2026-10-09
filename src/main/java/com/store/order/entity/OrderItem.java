package com.store.order.entity;

import com.store.product.entity.Product;
import com.store.product.entity.ProductVariant;
import com.store.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "variant_sku", length = 120)
    private String variantSku;

    @Column(name = "variant_option_name", length = 80)
    private String variantOptionName;

    @Column(name = "variant_option_value", length = 160)
    private String variantOptionValue;

    @Column(name = "variant_reservation_active", nullable = false)
    @Builder.Default
    private Boolean variantReservationActive = false;

    // Snapshots preserve the supplier mapping and source cost used at checkout.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "supplier_sku", nullable = false, length = 100)
    private String supplierSku;

    @Column(name = "supplier_unit_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal supplierUnitCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
