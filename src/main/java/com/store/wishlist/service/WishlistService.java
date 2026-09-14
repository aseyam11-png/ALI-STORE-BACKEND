package com.store.wishlist.service;

import com.store.wishlist.dto.WishlistRequest;
import com.store.wishlist.dto.WishlistResponse;
import com.store.wishlist.entity.Wishlist;
import com.store.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    public List<WishlistResponse> getUserWishlist(Long userId) {
        return wishlistRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WishlistResponse addToWishlist(Long userId, WishlistRequest request) {
        if (wishlistRepository.existsByUserIdAndProductId(userId, request.getProductId())) {
            throw new RuntimeException("Product is already in wishlist");
        }

        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .productId(request.getProductId())
                .build();

        Wishlist saved = wishlistRepository.save(wishlist);
        return mapToResponse(saved);
    }

    @Transactional
    public void removeFromWishlist(Long userId, Long productId) {
        if (!wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new RuntimeException("Product not found in wishlist");
        }
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
    }

    private WishlistResponse mapToResponse(Wishlist wishlist) {
        return WishlistResponse.builder()
                .id(wishlist.getId())
                .userId(wishlist.getUserId())
                .productId(wishlist.getProductId())
                .createdAt(wishlist.getCreatedAt())
                .build();
    }
}