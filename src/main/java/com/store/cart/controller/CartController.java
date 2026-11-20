package com.store.cart.controller;

import com.store.cart.dto.CartItemRequest;
import com.store.cart.dto.CartResponse;
import com.store.cart.service.CartService;
import com.store.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getCartByUserEmail(userDetails.getUsername()),
                "Cart retrieved successfully"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<CartResponse>> replaceCart(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid java.util.List<@Valid CartItemRequest> requests) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.replaceCart(userDetails.getUsername(), requests),
                "Cart replaced successfully"));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.addItemToCart(userDetails.getUsername(), request),
                "Item added to cart"));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long itemId,
            @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.updateItemQuantity(userDetails.getUsername(), itemId, request),
                "Cart item updated"));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.removeItemFromCart(userDetails.getUsername(), itemId),
                "Item removed from cart"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.clearCart(userDetails.getUsername()),
                "Cart cleared"));
    }
}
