package com.store.wishlist.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
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
    private final ProductRepository productRepository;

    public List<WishlistResponse> getMyWishlist(String email) {
        Long userId = resolveUserId(email);
        return wishlistRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WishlistResponse addToWishlist(String email, WishlistRequest request) {
        Long userId = resolveUserId(email);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() != ProductStatus.ACTIVE
                || product.getCategory() == null
                || !Boolean.TRUE.equals(product.getCategory().getActive())
                || product.getSupplier() == null
                || product.getSupplier().getStatus() != com.store.supplier.entity.SupplierStatus.ACTIVE
                || !Boolean.TRUE.equals(product.getSupplier().getFulfillmentEnabled())) {
            throw new BusinessException("Product is not currently available");
        }

        if (wishlistRepository.existsByUserIdAndProductId(userId, request.getProductId())) {
            throw new ConflictException("Product is already in wishlist");
        }

        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .productId(request.getProductId())
                .build();

        return mapToResponse(wishlistRepository.save(wishlist));
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
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
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
