package com.store.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.service.JwtService;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.coupon.entity.Coupon;
import com.store.coupon.repository.CouponRepository;
import com.store.inventory.entity.Inventory;
import com.store.inventory.repository.InventoryRepository;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import com.store.inventory.enums.InventorySource;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-PostgreSQL concurrency verification, requested explicitly because the H2-backed
 * suite (CartOrderInventoryTest etc.) cannot prove these races: those tests are
 * @Transactional (rolled back per test), so worker threads spawned for genuine concurrency
 * get their own DB connections that cannot see the main thread's still-uncommitted setup
 * data - the race becomes unobservable rather than proven safe. This class has NO
 * @Transactional wrapper: setup data is committed via ordinary (auto-committing) repository
 * calls before each race is triggered, and Flyway runs its real V1-V3 migrations against a
 * genuine PostgreSQL instance (not H2's PostgreSQL-compatibility mode).
 *
 * Named "...IT" (not "...Test"/"...Tests") so Surefire's default include patterns skip it
 * during ordinary `mvn test` - it only runs when explicitly requested
 * (`mvn test -Dtest=PostgresConcurrencyIT`), because it requires a working Docker daemon.
 *
 * STATUS IN THIS REPOSITORY'S DEVELOPMENT ENVIRONMENT: attempted and BLOCKED. Docker
 * Desktop is installed but fails to start ("Docker Desktop is unable to start") - most
 * likely because this sandboxed session has no nested virtualization available. This class
 * has not been executed here; it is provided ready-to-run for any environment with a
 * working Docker daemon (local dev machine, CI runner). Do not treat its presence as proof
 * these races were verified against real PostgreSQL - see the stabilization report.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PostgresConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("store_db")
            .withUsername("store_test")
            .withPassword("store_test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        // A real, sufficiently strong secret for JwtService's startup validation - base
        // application.yml requires JWT_SECRET with no default, and this test activates no
        // profile that would otherwise supply one.
        registry.add("jwt.secret", () -> "dGVzdGNvbnRhaW5lcnMtcG9zdGdyZXMtaXQtc2lnbmluZy1rZXktbm90LWZvci1wcm9kdWN0aW9uLXVzZQ==");
    }

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
    @Autowired private CouponRepository couponRepository;

    private Role userRole() {
        return roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("Standard customer role").build()));
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .firstName("Test").lastName("User").email(email)
                .password(passwordEncoder.encode("Password123")).phoneNumber("0").isEnabled(true).role(userRole()).build());
    }

    private String tokenFor(User user) {
        UserDetails ud = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getName())));
        return jwtService.generateToken(ud);
    }

    private Product productWithStock(String sku, int quantity) {
        Category category = categoryRepository.save(Category.builder()
                .name("Category-" + sku).slug("category-" + sku.toLowerCase()).active(true).build());
        Supplier supplier = supplierRepository.save(Supplier.builder()
                .externalCode("SUP-" + sku).name("Test Supplier " + sku).build());
        Product product = productRepository.save(Product.builder()
                .name("Product " + sku).slug("product-" + sku.toLowerCase()).price(new BigDecimal("25.00"))
                .currency("USD").sku(sku).status(ProductStatus.ACTIVE).category(category)
                .supplier(supplier).supplierSku("SUP-SKU-" + sku).supplierCost(new BigDecimal("10.00")).build());
        inventoryRepository.save(Inventory.builder().product(product).quantity(quantity).reservedQuantity(0)
                .lowStockThreshold(5).source(InventorySource.MANUAL).lastSyncedAt(java.time.LocalDateTime.now()).build());
        return product;
    }

    @Test
    void flywayMigrations_applyCleanlyAgainstRealPostgres() {
        // The Spring context only finishes starting if Flyway's V1/V2/V3 migrations all
        // applied without error against the real container - reaching this assertion at all
        // is itself the verification. Confirms real PostgreSQL syntax compatibility (this
        // repo's migrations were previously only exercised via H2's PostgreSQL-compat mode).
        Assertions.assertTrue(postgres.isRunning());
    }

    @Test
    void checkout_concurrentDoubleSubmitFromSameCart_onlyEverCreatesOneOrder() throws Exception {
        User user = createUser("race-real-pg@example.com");
        Product product = productWithStock("RACE-PG-1", 10);

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 2))))
                .andExpect(status().isOk());

        String token = tokenFor(user);
        String body = objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> checkoutCall = () ->
                mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();

        List<Future<Integer>> futures = executor.invokeAll(List.of(checkoutCall, checkoutCall));
        executor.shutdown();

        long successCount = futures.stream().filter(f -> {
            try {
                return f.get() == 200;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).count();

        Assertions.assertEquals(1, successCount,
                "exactly one of two truly concurrent checkout requests against real PostgreSQL must succeed - proves CartRepository.findWithLockByUserId's pessimistic lock");
    }

    @Test
    void couponRedemption_concurrentRacesForLastRemainingUse_onlyOneSucceeds() throws Exception {
        User userA = createUser("coupon-race-pg-a@example.com");
        User userB = createUser("coupon-race-pg-b@example.com");
        Product productA = productWithStock("COUPON-PG-A", 5);
        Product productB = productWithStock("COUPON-PG-B", 5);

        couponRepository.save(Coupon.builder()
                .code("PGRACE").discountPercentage(new BigDecimal("10.00"))
                .expirationDate(java.time.LocalDateTime.now().plusDays(30)).isActive(true)
                .usageLimit(1).timesUsed(0).build());

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", productA.getId(), "quantity", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", productB.getId(), "quantity", 1))))
                .andExpect(status().isOk());

        String tokenA = tokenFor(userA);
        String tokenB = tokenFor(userB);
        String bodyA = objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "PGRACE"));
        String bodyB = objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "PGRACE"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = executor.invokeAll(List.of(
                (Callable<Integer>) () -> mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenA)
                                .contentType(MediaType.APPLICATION_JSON).content(bodyA))
                        .andReturn().getResponse().getStatus(),
                (Callable<Integer>) () -> mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenB)
                                .contentType(MediaType.APPLICATION_JSON).content(bodyB))
                        .andReturn().getResponse().getStatus()
        ));
        executor.shutdown();

        long successCount = futures.stream().filter(f -> {
            try {
                return f.get() == 200;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).count();

        Assertions.assertEquals(1, successCount,
                "exactly one of two different users truly-concurrently redeeming a usageLimit=1 coupon against real PostgreSQL must succeed - proves CouponRepository.tryRedeem's atomic UPDATE ... WHERE");

        Coupon persisted = couponRepository.findByCode("PGRACE").orElseThrow();
        Assertions.assertEquals(1, persisted.getTimesUsed());
    }

    @Test
    void payment_concurrentReservationForSameOrder_onlyOneSucceeds() throws Exception {
        // Proves PaymentReservationService's REQUIRES_NEW reservation + payments.order_id's
        // unique constraint against a REAL database: two truly concurrent processPayment
        // calls for the SAME order must not both create a payment row. This verifies
        // DB-level duplicate-reservation protection only - it says nothing about whether a
        // real payment gateway would be charged twice, since MockPaymentProvider has no
        // persistent state and does not implement idempotency-key deduplication (see
        // PaymentProvider#charge's javadoc). That remains NOT VERIFIED / provider-dependent.
        User user = createUser("payment-race-pg@example.com");
        Product product = productWithStock("PAYMENT-PG-1", 5);

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());

        String token = tokenFor(user);
        String orderResponse = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderResponse).path("data").path("id").asLong();
        java.math.BigDecimal totalAmount = new java.math.BigDecimal(
                objectMapper.readTree(orderResponse).path("data").path("totalAmount").asText());

        String paymentBody = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId, "amount", totalAmount, "method", "CREDIT_CARD"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> payCall = () ->
                mockMvc.perform(post("/api/v1/payments").header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(paymentBody))
                        .andReturn().getResponse().getStatus();

        List<Future<Integer>> futures = executor.invokeAll(List.of(payCall, payCall));
        executor.shutdown();

        long successCount = futures.stream().filter(f -> {
            try {
                return f.get() == 201;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).count();

        Assertions.assertEquals(1, successCount,
                "exactly one of two truly concurrent payment requests for the same order against real PostgreSQL must succeed - proves the reservation + unique-constraint pairing at the DB level only");
    }
}
