package com.store.wishlist.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WishlistResponse {
    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime createdAt;
}