package com.store.seller.dto;

import com.store.seller.enums.SellerStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SellerResponse {
    private Long id;
    private Long userId;
    private String storeName;
    private String taxId;
    private SellerStatus status;
    private LocalDateTime createdAt;
}