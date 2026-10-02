package com.store.b2b.entity;

import com.store.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rfq_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RFQItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_id", nullable = false)
    private RFQ rfq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}