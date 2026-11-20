package com.store.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.service.JwtService;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.InventorySource;
import com.store.inventory.repository.InventoryRepository;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import com.store.user.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartProductLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private InventoryRepository inventoryRepository;

    private Role userRole;
    private Category category;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("Customer").build()));
        category = categoryRepository.save(Category.builder().name("Lifecycle Test").slug("lifecycle-test").active(true).build());
        supplier = supplierRepository.save(Supplier.builder()
                .externalCode("LIFECYCLE-SUPPLIER")
                .name("Lifecycle Supplier")
                .status(com.store.supplier.entity.SupplierStatus.ACTIVE)
                .fulfillmentEnabled(true)
                .inventorySyncEnabled(true)
                .build());
    }

    private User user() {
        return userRepository.save(User.builder()
                .firstName("Test").lastName("Customer").email("lifecycle@example.com")
                .password(passwordEncoder.encode("Password123"))
                .phoneNumber("0").isEnabled(true).role(userRole).build());
    }

    private String token(User user) {
        return jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getName()))));
    }

    private Product product(ProductStatus status) {
        Product product = productRepository.save(Product.builder()
                .name("Lifecycle Product")
                .slug("lifecycle-product-" + status.name().toLowerCase())
                .price(new BigDecimal("25.00"))
                .currency("USD")
                .sku("LIFE-" + status.name())
                .status(status)
                .category(category)
                .supplier(supplier)
                .supplierSku("SUP-LIFE-" + status.name())
                .supplierCost(new BigDecimal("10.00"))
                .build());
        inventoryRepository.save(Inventory.builder()
                .product(product).quantity(10).reservedQuantity(0).lowStockThreshold(2)
                .source(InventorySource.MANUAL).lastSyncedAt(java.time.LocalDateTime.now()).build());
        return product;
    }

    @Test
    void draftProductCannotBeAddedToCart() throws Exception {
        User user = user();
        Product product = product(ProductStatus.DRAFT);

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedProductCannotBeAddedToCart() throws Exception {
        User user = user();
        Product product = product(ProductStatus.ARCHIVED);

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isNotFound());
    }
}
