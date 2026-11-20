package com.store.brand.entity;

import com.store.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "brands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Brand extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "logo_url")
    private String logoUrl;
}