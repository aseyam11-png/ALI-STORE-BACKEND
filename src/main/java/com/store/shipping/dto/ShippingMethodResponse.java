package com.store.shipping.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class ShippingMethodResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer estimatedMinDays;
    private Integer estimatedMaxDays;
    private String countryCode;
    private Boolean active;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}