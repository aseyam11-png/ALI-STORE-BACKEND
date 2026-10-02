package com.store.cart.service;

import com.store.cart.dto.CartItemRequest;
import com.store.cart.dto.CartItemResponse;
import com.store.cart.dto.CartResponse;
import com.store.cart.entity.Cart;
import com.store.cart.entity.CartItem;
import com.store.cart.repository.CartItemRepository;
import com.store.cart.repository.CartRepository;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.inventory.service.InventoryService;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
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
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public CartResponse getCartByUserEmail(String email) {
        Cart cart = cartRepository.findByUserId(resolveUserId(email)).orElse(null);
        if (cart == null) {
            return CartResponse.builder()
                    .id(null)
                    .items(List.of())
                    .totalPrice(BigDecimal.ZERO)
                    .build();
        }
        return mapToResponse(cart.getId());
    }

    @Transactional
    public CartResponse addItemToCart(String email, CartItemRequest request) {
        Cart cart = getOrCreateCartLocked(email);
        Product product = productRepository.findById(request.getProductId())
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active product not found with id: " + request.getProductId()));

        int available = inventoryService.getAvailableQuantity(product.getId());
        if (available < request.getQuantity()) {
            throw new BusinessException("Insufficient stock for product: " + product.getName());
        }

        List<CartItem> existingItems = cartItemRepository.findByCartId(cart.getId());
        String productCurrency = product.getCurrency();
        if (existingItems.stream()
                .map(item -> item.getProduct().getCurrency())
                .filter(java.util.Objects::nonNull)
                .anyMatch(currency -> !currency.equalsIgnoreCase(productCurrency))) {
            throw new BusinessException("A cart cannot contain products in different currencies");
        }

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(CartItem.builder().cart(cart).product(product).quantity(0).build());

        int nextQuantity = cartItem.getQuantity() + request.getQuantity();
        if (nextQuantity > available) {
            throw new BusinessException("Cart quantity exceeds currently available stock");
        }

        cartItem.setQuantity(nextQuantity);
        cartItemRepository.save(cartItem);
        return mapToResponse(cart.getId());
    }

    @Transactional
    public CartResponse updateItemQuantity(String email, Long itemId, CartItemRequest request) {
        Cart cart = getOrCreateCartLocked(email);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));

        assertOwnership(cart, item);

        int available = inventoryService.getAvailableQuantity(item.getProduct().getId());
        if (request.getQuantity() > available) {
            throw new BusinessException("Requested quantity exceeds currently available stock");
        }

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return mapToResponse(cart.getId());
    }

    @Transactional
    public CartResponse removeItemFromCart(String email, Long itemId) {
        Cart cart = getOrCreateCartLocked(email);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));
        assertOwnership(cart, item);
        cartItemRepository.delete(item);
        return mapToResponse(cart.getId());
    }

    @Transactional
    public CartResponse clearCart(String email) {
        Cart cart = getOrCreateCartLocked(email);
        cartItemRepository.deleteByCartId(cart.getId());
        return mapToResponse(cart.getId());
    }

    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }

    private Cart getOrCreateCartLocked(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return cartRepository.findWithLockByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
    }

    private void assertOwnership(Cart cart, CartItem item) {
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new ForbiddenException("This cart item does not belong to the current user");
        }
    }

    private CartResponse mapToResponse(Long cartId) {
        List<CartItemResponse> itemResponses = cartItemRepository.findByCartId(cartId).stream()
                .map(item -> {
                    BigDecimal subTotal = item.getProduct().getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));
                    return CartItemResponse.builder()
                            .id(item.getId())
                            .productId(item.getProduct().getId())
                            .productName(item.getProduct().getName())
                            .productPrice(item.getProduct().getPrice())
                            .currency(item.getProduct().getCurrency())
                            .quantity(item.getQuantity())
                            .subTotal(subTotal)
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal totalPrice = itemResponses.stream()
                .map(CartItemResponse::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = itemResponses.stream()
                .map(CartItemResponse::getCurrency)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        return CartResponse.builder()
                .id(cartId)
                .items(itemResponses)
                .totalPrice(totalPrice)
                .currency(currency)
                .build();
    }
}
