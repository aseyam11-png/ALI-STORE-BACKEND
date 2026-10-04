package com.store.order;

import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.cart.entity.Cart;
import com.store.cart.entity.CartItem;
import com.store.cart.repository.CartItemRepository;
import com.store.cart.repository.CartRepository;
import com.store.common.exception.BusinessException;
import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.InventorySource;
import com.store.inventory.repository.InventoryRepository;
import com.store.order.dto.OrderRequest;
import com.store.order.dto.OrderResponse;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.order.service.OrderService;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCurrencyContractTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void createOrder_persistsAndReturnsCanonicalCurrency() {
        User user = user("currency@example.com");
        Supplier supplier = supplier("CURRENCY-SUPPLIER");
        Category category = category("currency-category");
        Product product = product("EUR-PRODUCT", "EUR", supplier, category);

        Cart cart = cartRepository.save(Cart.builder().user(user).build());
        cartItemRepository.save(CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(1)
                .build());

        OrderRequest request = new OrderRequest();
        request.setShippingAddress("1 Main Street");

        OrderResponse response =
                orderService.createOrder(user.getEmail(), request, "currency-contract-1");

        Assertions.assertEquals("EUR", response.getCurrency());

        Order persisted =
                orderRepository.findById(response.getId()).orElseThrow();
        Assertions.assertEquals("EUR", persisted.getCurrency());
    }

    @Test
    void createOrder_rejectsMixedCurrenciesBeforeCreatingOrder() {
        User user = user("mixed-currency@example.com");
        Supplier supplier = supplier("MIXED-CURRENCY-SUPPLIER");
        Category category = category("mixed-currency-category");

        Product usd = product("USD-PRODUCT", "USD", supplier, category);
        Product eur = product("EUR-PRODUCT-2", "EUR", supplier, category);

        Cart cart = cartRepository.save(Cart.builder().user(user).build());
        cartItemRepository.save(CartItem.builder()
                .cart(cart)
                .product(usd)
                .quantity(1)
                .build());
        cartItemRepository.save(CartItem.builder()
                .cart(cart)
                .product(eur)
                .quantity(1)
                .build());

        OrderRequest request = new OrderRequest();
        request.setShippingAddress("1 Main Street");

        Assertions.assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        user.getEmail(), request, "mixed-currency-contract-1"));

        Assertions.assertEquals(0, orderRepository.findByUserIdAndIdempotencyKey(
                user.getId(), "mixed-currency-contract-1").stream().count());
    }

    private User user(String email) {
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("Standard customer role")
                        .build()));

        return userRepository.save(User.builder()
                .firstName("Order")
                .lastName("Currency")
                .email(email)
                .password(passwordEncoder.encode("Password123"))
                .phoneNumber("0599000000")
                .isEnabled(true)
                .role(role)
                .build());
    }

    private Supplier supplier(String code) {
        return supplierRepository.save(Supplier.builder()
                .externalCode(code)
                .name("Supplier " + code)
                .apiBaseUrl("https://supplier.example")
                .status(SupplierStatus.ACTIVE)
                .fulfillmentEnabled(true)
                .inventorySyncEnabled(true)
                .build());
    }

    private Category category(String slug) {
        return categoryRepository.save(Category.builder()
                .name("Currency " + slug)
                .slug(slug)
                .active(true)
                .build());
    }

    private Product product(
            String sku,
            String currency,
            Supplier supplier,
            Category category) {
        Product product = productRepository.save(Product.builder()
                .name("Product " + sku)
                .slug("product-" + sku.toLowerCase())
                .description("Currency contract test product")
                .price(new BigDecimal("25.00"))
                .currency(currency)
                .sku(sku)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .supplier(supplier)
                .supplierSku("SUP-" + sku)
                .supplierCost(new BigDecimal("10.00"))
                .build());

        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(10)
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .source(InventorySource.MANUAL)
                .build());

        return product;
    }
}
