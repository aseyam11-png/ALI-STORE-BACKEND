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
import com.store.product.entity.ProductVariant;
import com.store.product.repository.ProductVariantRepository;
import com.store.pricing.service.PromotionService;
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
    private final ProductVariantRepository productVariantRepository;
    private final PromotionService promotionService;

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

    /**
     * Replaces the complete persisted cart atomically.
     *
     * Every product, quantity and currency constraint is validated before the
     * existing cart rows are deleted, so a rejected item can never leave a
     * partially rebuilt cart.
     */
    @Transactional
    public CartResponse replaceCart(String email, List<CartItemRequest> requests) {
        Cart cart = getOrCreateCartLocked(email);

        if (requests == null) {
            throw new BusinessException("Cart items are required");
        }

        java.util.Set<String> cartKeys = new java.util.HashSet<>();
        String currency = null;
        List<CartItem> replacementItems = new java.util.ArrayList<>();

        for (CartItemRequest request : requests) {
            if (request == null || request.getProductId() == null || request.getQuantity() == null
                    || request.getQuantity() < 1) {
                throw new BusinessException("Every cart item must contain a valid product and quantity");
            }

            ProductVariant variant = resolveVariant(request.getProductId(), request.getVariantId());
            String cartKey = request.getProductId() + ":" + (variant == null ? "base" : variant.getId());
            if (!cartKeys.add(cartKey)) {
                throw new BusinessException("A product variant may appear only once in the cart");
            }

            Product product = productRepository.findById(request.getProductId())
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active product not found with id: " + request.getProductId()));

            int productAvailable = inventoryService.getAvailableQuantity(product.getId());
            int available = variant == null ? productAvailable
                    : Math.min(productAvailable, variant.getAvailableQuantity());
            if (request.getQuantity() > available) {
                throw new BusinessException("Cart quantity exceeds currently available stock");
            }

            String productCurrency = product.getCurrency();
            if (productCurrency == null || productCurrency.isBlank()) {
                throw new BusinessException("Product currency is not configured");
            }

            if (currency == null) {
                currency = productCurrency;
            } else if (!currency.equalsIgnoreCase(productCurrency)) {
                throw new BusinessException("A cart cannot contain products in different currencies");
            }

            replacementItems.add(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .quantity(request.getQuantity())
                    .build());
        }

        cartItemRepository.deleteByCartId(cart.getId());
        if (!replacementItems.isEmpty()) {
            cartItemRepository.saveAll(replacementItems);
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

        ProductVariant variant = resolveVariant(request.getProductId(), request.getVariantId());
        int productAvailable = inventoryService.getAvailableQuantity(product.getId());
        int available = variant == null ? productAvailable
                : Math.min(productAvailable, variant.getAvailableQuantity());
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

        CartItem cartItem = cartItemRepository.findByCartIdAndProductIdAndVariantId(
                        cart.getId(), product.getId(), request.getVariantId())
                .orElse(CartItem.builder().cart(cart).product(product).variant(variant).quantity(0).build());

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

        int productAvailable = inventoryService.getAvailableQuantity(item.getProduct().getId());
        int available = item.getVariant() == null ? productAvailable
                : Math.min(productAvailable, item.getVariant().getAvailableQuantity());
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

    private ProductVariant resolveVariant(Long productId, Long variantId) {
        if (variantId == null) return null;
        return productVariantRepository.findByIdAndProductId(variantId, productId)
                .filter(ProductVariant::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active product variant not found"));
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
                    BigDecimal unitPrice = promotionService.calculate(
                            item.getProduct(), item.getVariant()).effectivePrice();
                    BigDecimal subTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                    return CartItemResponse.builder()
                            .id(item.getId())
                            .productId(item.getProduct().getId())
                            .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                            .variantName(item.getVariant() != null ? item.getVariant().getOptionName() : null)
                            .variantValue(item.getVariant() != null ? item.getVariant().getOptionValue() : null)
                            .variantSku(item.getVariant() != null ? item.getVariant().getSku() : null)
                            .productName(item.getProduct().getName())
                            .productPrice(unitPrice)
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
