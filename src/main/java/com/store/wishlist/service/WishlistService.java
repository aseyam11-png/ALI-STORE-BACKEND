package com.store.wishlist.service;

import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.product.entity.Product;
import com.store.product.entity.ProductImage;
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
                .filter(candidate -> candidate.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active product not found"));

        if (wishlistRepository.existsByUserIdAndProductId(userId, product.getId())) {
            throw new ConflictException("Product is already in wishlist");
        }

        Wishlist saved = wishlistRepository.save(Wishlist.builder()
                .userId(userId)
                .productId(product.getId())
                .build());

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
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }

    private WishlistResponse mapToResponse(Wishlist wishlist) {
        Product product = productRepository.findById(wishlist.getProductId()).orElse(null);

        return WishlistResponse.builder()
                .id(wishlist.getId())
                .userId(wishlist.getUserId())
                .productId(wishlist.getProductId())
                .productName(product == null ? null : product.getName())
                .price(product == null ? null : product.getPrice())
                .currency(product == null ? null : product.getCurrency())
                .imageUrl(product == null || product.getImages().isEmpty()
                        ? null
                        : product.getImages().stream()
                                .sorted(java.util.Comparator.comparing(ProductImage::getSortOrder))
                                .findFirst()
                                .map(ProductImage::getUrl)
                                .orElse(null))
                .brandName(product == null || product.getBrand() == null ? null : product.getBrand().getName())
                .categoryName(product == null || product.getCategory() == null ? null : product.getCategory().getName())
                .createdAt(wishlist.getCreatedAt())
                .build();
    }
}
