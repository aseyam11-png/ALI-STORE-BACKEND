package com.store.wishlist.service;

import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
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
        List<Wishlist> items = wishlistRepository.findByUserId(userId);
        var products = productRepository.findAllById(
                items.stream().map(Wishlist::getProductId).toList()
        ).stream().collect(Collectors.toMap(Product::getId, product -> product));

        return items.stream()
                .map(item -> mapToResponse(item, products.get(item.getProductId())))
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

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        Wishlist saved = wishlistRepository.save(wishlist);
        return mapToResponse(saved, product);
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

    private WishlistResponse mapToResponse(Wishlist wishlist, Product product) {
        return WishlistResponse.builder()
                .id(wishlist.getId())
                .userId(wishlist.getUserId())
                .productId(wishlist.getProductId())
                .createdAt(wishlist.getCreatedAt())
                .productName(product != null ? product.getName() : null)
                .price(product != null ? product.getPrice() : null)
                .currency(product != null ? product.getCurrency() : null)
                .discountPercentage(product != null ? product.getDiscountPercentage() : null)
                .imageUrl(product != null
                        ? product.getImages().stream()
                            .sorted(java.util.Comparator.comparing(com.store.product.entity.ProductImage::getSortOrder))
                            .findFirst()
                            .map(com.store.product.entity.ProductImage::getUrl)
                            .orElse(null)
                        : null)
                .brandName(product != null && product.getBrand() != null ? product.getBrand().getName() : null)
                .categoryName(product != null && product.getCategory() != null ? product.getCategory().getName() : null)
                .build();
    }
}
