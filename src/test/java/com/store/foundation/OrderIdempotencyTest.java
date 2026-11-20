package com.store.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.service.JwtService;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.InventorySource;
import com.store.inventory.repository.InventoryRepository;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import com.store.user.repository.RoleRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderIdempotencyTest {

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
    @Autowired private OrderRepository orderRepository;

    private Role userRole;
    private Category category;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("Standard customer role")
                        .build()));

        category = categoryRepository.findByName("Idempotency Test")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Idempotency Test")
                        .slug("idempotency-test")
                        .active(true)
                        .build()));

        supplier = supplierRepository.findByExternalCode("IDEMPOTENCY-SUPPLIER")
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .externalCode("IDEMPOTENCY-SUPPLIER")
                        .name("Idempotency Test Supplier")
                        .status(com.store.supplier.entity.SupplierStatus.ACTIVE)
                        .fulfillmentEnabled(true)
                        .inventorySyncEnabled(true)
                        .build()));
    }

    @Test
    void checkoutRetryWithSameKeyReturnsTheOriginalOrderWithoutCreatingAnotherOrder() throws Exception {
        User user = userRepository.save(User.builder()
                .firstName("Test")
                .lastName("Customer")
                .email("idempotency@example.com")
                .password(passwordEncoder.encode("Password123"))
                .phoneNumber("0")
                .isEnabled(true)
                .role(userRole)
                .build());

        Product product = productRepository.save(Product.builder()
                .name("Idempotency Product")
                .slug("idempotency-product")
                .price(new BigDecimal("25.00"))
                .currency("USD")
                .sku("IDEMP-001")
                .status(ProductStatus.ACTIVE)
                .category(category)
                .supplier(supplier)
                .supplierSku("SUP-IDEMP-001")
                .supplierCost(new BigDecimal("10.00"))
                .build());

        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(3)
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .source(InventorySource.MANUAL)
                .lastSyncedAt(java.time.LocalDateTime.now())
                .build());

        String token = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getName()))));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", product.getId(),
                                "quantity", 1))))
                .andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(Map.of("shippingAddress", "Gaza, Palestine"));

        String firstResponse = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "checkout-2026-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long firstOrderId = objectMapper.readTree(firstResponse).path("data").path("id").asLong();

        String retryResponse = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "checkout-2026-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstOrderId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long retryOrderId = objectMapper.readTree(retryResponse).path("data").path("id").asLong();
        Assertions.assertEquals(firstOrderId, retryOrderId);
        Assertions.assertEquals(1, orderRepository.findByUserId(user.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements());

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        Assertions.assertEquals(1, inventory.getReservedQuantity());
        Assertions.assertEquals(3, inventory.getQuantity());
    }
}
