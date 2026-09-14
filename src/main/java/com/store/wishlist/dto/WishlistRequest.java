package com.store.wishlist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WishlistRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;
}