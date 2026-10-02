package com.store.shipping.entity;

import com.store.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipping_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String city;

    private String state;

    @Column(nullable = false)
    private String country;

    private String postalCode;

    private Boolean isDefault;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}