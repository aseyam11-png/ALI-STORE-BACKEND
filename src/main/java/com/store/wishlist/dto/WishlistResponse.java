package com.store.wishlist.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WishlistResponse {
    private Long id;
    private Long userId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private String currency;
    private String imageUrl;
    private String brandName;
    private String categoryName;
    private LocalDateTime createdAt;
}
