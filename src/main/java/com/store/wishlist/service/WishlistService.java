package com.store.wishlist.service;

import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    public List<WishlistResponse> getMyWishlist(String email) {
        Long userId = resolveUserId(email);
        return wishlistRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WishlistResponse addToWishlist(String email, WishlistRequest request) {
        Long userId = resolveUserId(email);

        if (wishlistRepository.existsByUserIdAndProductId(userId, request.getProductId())) {
            throw new ConflictException("Product is already in wishlist");
        }

        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .productId(request.getProductId())
                .build();

        Wishlist saved = wishlistRepository.save(wishlist);
        return mapToResponse(saved);
    }

    @Transactional
    public void removeFromWishlist(String email, Long productId) {
        Long userId = resolveUserId(email);

        if (!wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new ResourceNotFoundException("Product not found in wishlist");
        }
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
    }

    private Long resolveUserId(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
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
