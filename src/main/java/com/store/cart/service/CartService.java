package com.store.cart.service;

import com.store.cart.dto.CartItemRequest;
import com.store.cart.dto.CartItemResponse;
import com.store.cart.dto.CartResponse;
import com.store.cart.entity.Cart;
import com.store.cart.entity.CartItem;
import com.store.cart.repository.CartItemRepository;
import com.store.cart.repository.CartRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public CartResponse getCartByUserEmail(String email) {
        Cart cart = getOrCreateCart(email);
        return mapToResponse(cart);
    }

    @Transactional
    public CartResponse addItemToCart(String email, CartItemRequest request) {
        Cart cart = getOrCreateCart(email);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new RuntimeException("Insufficient stock");
        }

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(CartItem.builder().cart(cart).product(product).quantity(0).build());

        cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        cartItemRepository.save(cartItem);

        return mapToResponse(cartRepository.findById(cart.getId()).orElse(cart));
    }

    @Transactional
    public CartResponse removeItemFromCart(String email, Long itemId) {
        Cart cart = getOrCreateCart(email);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Unauthorized action");
        }

        cartItemRepository.delete(item);
        return mapToResponse(cartRepository.findById(cart.getId()).orElse(cart));
    }

    private Cart getOrCreateCart(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream().map(item -> {
            BigDecimal subTotal = item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return CartItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productPrice(item.getProduct().getPrice())
                    .quantity(item.getQuantity())
                    .subTotal(subTotal)
                    .build();
        }).collect(Collectors.toList());

        BigDecimal totalPrice = itemResponses.stream()
                .map(CartItemResponse::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .items(itemResponses)
                .totalPrice(totalPrice)
                .build();
    }
}