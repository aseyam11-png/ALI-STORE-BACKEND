package com.store.wishlist.controller;

import com.store.common.response.ApiResponse;
import com.store.wishlist.dto.WishlistRequest;
import com.store.wishlist.dto.WishlistResponse;
import com.store.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WishlistResponse>>> getUserWishlist(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(wishlistService.getUserWishlist(userId), "Wishlist retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WishlistResponse>> addToWishlist(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody WishlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(wishlistService.addToWishlist(userId, request), "Added to wishlist successfully"));
    }

    @DeleteMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long productId) {
        wishlistService.removeFromWishlist(userId, productId);
        return ResponseEntity.ok(ApiResponse.success(null, "Removed from wishlist successfully"));
    }
}