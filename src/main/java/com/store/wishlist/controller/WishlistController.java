package com.store.wishlist.controller;

import com.store.common.response.ApiResponse;
import com.store.wishlist.dto.WishlistRequest;
import com.store.wishlist.dto.WishlistResponse;
import com.store.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WishlistResponse>>> getMyWishlist(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(wishlistService.getMyWishlist(userDetails.getUsername()), "Wishlist retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WishlistResponse>> addToWishlist(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WishlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(wishlistService.addToWishlist(userDetails.getUsername(), request), "Added to wishlist successfully"));
    }

    @DeleteMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long productId) {
        wishlistService.removeFromWishlist(userDetails.getUsername(), productId);
        return ResponseEntity.ok(ApiResponse.success(null, "Removed from wishlist successfully"));
    }
}
