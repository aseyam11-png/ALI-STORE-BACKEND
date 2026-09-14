package com.store.brand.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BrandResponse {
    private Long id;
    private String name;
    private String description;
    private String logoUrl;
}