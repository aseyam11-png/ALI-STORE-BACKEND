package com.store.foundation;

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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cart/order/inventory invariants: stock is only ever checked and deducted
 * through Inventory (never Product), order ownership is principal-based, and
 * a cart item cannot be manipulated by anyone other than the cart's owner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartOrderInventoryTest {

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

    private Role userRole;
    private Role adminRole;
    private Category category;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("Standard customer role").build()));
        adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Administrator role").build()));
        category = categoryRepository.findByName("General")
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name("General").slug("general").active(true).build()));
        supplier = supplierRepository.findByExternalCode("TEST-SUPPLIER")
                .orElseGet(() -> supplierRepository.save(
                        Supplier.builder().externalCode("TEST-SUPPLIER").name("Test Supplier").build()));
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .firstName("Test").lastName("User").email(email)
                .password(passwordEncoder.encode("Password123")).phoneNumber("0").isEnabled(true).role(userRole).build());
    }

    private User createAdmin(String email) {
        return userRepository.save(User.builder()
                .firstName("Admin").lastName("User").email(email)
                .password(passwordEncoder.encode("Password123")).phoneNumber("0").isEnabled(true).role(adminRole).build());
    }

    private String tokenFor(User user) {
        UserDetails ud = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getName())));
        return jwtService.generateToken(ud);
    }

    private Product productWithStock(String sku, int quantity) {
        Product product = productRepository.save(Product.builder()
                .name("Product " + sku).slug("product-" + sku.toLowerCase()).price(new BigDecimal("25.00"))
                .currency("USD").sku(sku).status(ProductStatus.ACTIVE).category(category)
                .supplier(supplier).supplierSku("SUP-" + sku).supplierCost(new BigDecimal("10.00")).build());
        inventoryRepository.save(Inventory.builder().product(product).quantity(quantity).reservedQuantity(0).lowStockThreshold(5)
                .source(InventorySource.MANUAL).lastSyncedAt(java.time.LocalDateTime.now()).build());
        return product;
    }

    @Test
    void addToCart_rejectsInsufficientStock() throws Exception {
        User user = createUser("stockcheck@example.com");
        Product product = productWithStock("STOCK-1", 2);

        Map<String, Object> body = Map.of("productId", product.getId(), "quantity", 5);
        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addToCart_rejectsUnknownProduct() throws Exception {
        User user = createUser("unknownproduct@example.com");
        Map<String, Object> body = Map.of("productId", 999999, "quantity", 1);
        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cartItem_cannotBeRemovedByAnotherUser() throws Exception {
        User owner = createUser("cartowner@example.com");
        User intruder = createUser("cartintruder@example.com");
        Product product = productWithStock("CART-1", 10);

        String addBody = objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1));
        String response = mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(addBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long itemId = objectMapper.readTree(response).path("data").path("items").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/v1/cart/items/" + itemId).header("Authorization", "Bearer " + tokenFor(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void orderCreation_rejectsEmptyCart() throws Exception {
        User user = createUser("emptycart@example.com");
        Map<String, Object> body = Map.of("shippingAddress", "1 Nowhere St");
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderCreation_reservesInventory_andEnforcesOwnership() throws Exception {
        User owner = createUser("orderowner@example.com");
        User intruder = createUser("orderintruder@example.com");
        Product product = productWithStock("ORDER-1", 5);

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 3))))
                .andExpect(status().isOk());

        String orderResponse = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(75.0))
                .andReturn().getResponse().getContentAsString();

        long orderId = objectMapper.readTree(orderResponse).path("data").path("id").asLong();

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        Assertions.assertEquals(5, inventory.getQuantity());
        Assertions.assertEquals(3, inventory.getReservedQuantity());

        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + tokenFor(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addToCart_secondAddOfSameProduct_accumulatesOnOneLineInsteadOfDuplicating() throws Exception {
        // Regression test for the historical "first cart item" bug: adding a product that is
        // already in the cart must increment that existing CartItem's quantity (read fresh from
        // the repository), not silently no-op on a stale in-memory reference or create a duplicate row.
        User user = createUser("repeatadd@example.com");
        Product product = productWithStock("REPEAT-1", 10);

        String firstAddResponse = mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andReturn().getResponse().getContentAsString();
        long firstItemId = objectMapper.readTree(firstAddResponse).path("data").path("items").get(0).path("id").asLong();

        String secondAddResponse = mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andReturn().getResponse().getContentAsString();
        long secondItemId = objectMapper.readTree(secondAddResponse).path("data").path("items").get(0).path("id").asLong();

        Assertions.assertEquals(firstItemId, secondItemId, "adding the same product twice must reuse the same cart item row");
    }

    @Test
    void orderCreation_rejectsWhenStockDepletedAfterCartAdd() throws Exception {
        User user = createUser("depleted@example.com");
        Product product = productWithStock("DEPLETE-1", 1);

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());

        // Simulate another concurrent purchase depleting stock between add-to-cart and checkout.
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        inventory.setQuantity(0);
        inventoryRepository.saveAndFlush(inventory);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void couponRedemption_rollsBackWhenOrderCreationFailsLaterInTheSameTransaction() throws Exception {
        // OrderService.createOrder redeems the coupon (CouponService.redeem, an atomic
        // UPDATE ... WHERE) BEFORE the per-item inventory deduction loop runs. If inventory
        // then turns out to be insufficient, the whole @Transactional method rolls back -
        // this must undo the redemption's UPDATE along with everything else, not leave the
        // coupon's usage counter permanently decremented for an order that never happened.
        User user = createUser("couponrollback@example.com");
        Product product = productWithStock("COUPON-ROLLBACK-1", 1);

        couponRepository.save(Coupon.builder()
                .code("ROLLBACK10").discountPercentage(new java.math.BigDecimal("10.00"))
                .expirationDate(java.time.LocalDateTime.now().plusDays(30)).isActive(true)
                .usageLimit(5).timesUsed(0).build());

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());

        // Commit the setup data first. The failing checkout transaction is then
        // isolated, allowing us to verify that coupon redemption rolls back at the
        // database boundary rather than merely seeing rollback-only state in memory.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        // Simulate another purchase reserving the only available unit before checkout,
        // so the coupon is validated/redeemed successfully but the later inventory check fails.
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        inventory.setReservedQuantity(1);
        inventoryRepository.saveAndFlush(inventory);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "ROLLBACK10"))))
                .andExpect(status().isBadRequest());

        // The test class itself is transactional, so the service marks the current
        // transaction rollback-only. End that transaction before checking the persisted
        // state; otherwise the assertion can still see the uncommitted UPDATE.
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();

        Coupon persisted = couponRepository.findByCode("ROLLBACK10").orElseThrow();
        Assertions.assertEquals(0, persisted.getTimesUsed(),
                "a coupon redemption must roll back along with the rest of the failed order-creation transaction");
    }

    @Test
    void adminOrderStatusUpdate_allowsLegalTransitionButRejectsIllegalOneAndNonAdmins() throws Exception {
        User owner = createUser("statustransition@example.com");
        User admin = createAdmin("orderstatusadmin@example.com");
        Product product = productWithStock("STATUS-1", 5);

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());

        String orderResponse = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderResponse).path("data").path("id").asLong();

        // A regular customer must not be able to drive order fulfillment.
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PROCESSING"))))
                .andExpect(status().isForbidden());

        // PENDING -> SHIPPED skips PROCESSING and must be rejected as an illegal transition.
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SHIPPED"))))
                .andExpect(status().isBadRequest());

        // Fulfillment cannot start until a payment is recorded and confirmed.
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PROCESSING"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/payments").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", orderId,
                                "amount", 25.00,
                                "method", "CASH_ON_DELIVERY"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SHIPPED"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DELIVERED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));

        // DELIVERED is terminal - no transition back to PENDING (or anywhere else) is allowed.
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PENDING"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shipments_areAdminManagedAndOwnershipCheckedOnRead() throws Exception {
        // Regression test for a CRITICAL IDOR: ShipmentController previously had zero access
        // control, letting any authenticated customer create/mutate shipments for any order
        // and read any other customer's shipment/tracking data.
        User owner = createUser("shipmentowner@example.com");
        User intruder = createUser("shipmentintruder@example.com");
        User admin = createAdmin("shipmentadmin@example.com");
        Product product = productWithStock("SHIP-1", 5);

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());
        String orderResponse = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderResponse).path("data").path("id").asLong();

        String addressResponse = mockMvc.perform(post("/api/v1/shipping-addresses").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("street", "1 Main St", "city", "Gaza", "country", "PS"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long addressId = objectMapper.readTree(addressResponse).path("data").path("id").asLong();

        Map<String, Object> shipmentBody = Map.of("orderId", orderId, "supplierId", supplier.getId(), "shippingAddressId", addressId, "carrier", "DHL");

        // A customer must not be able to create a shipment at all.
        mockMvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shipmentBody)))
                .andExpect(status().isForbidden());

        String shipmentResponse = mockMvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shipmentBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long shipmentId = objectMapper.readTree(shipmentResponse).path("data").path("id").asLong();

        // A customer must not be able to change shipment status (e.g. mark it DELIVERED).
        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(owner))
                        .param("status", "DELIVERED"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .param("status", "PROCESSING"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .param("status", "SHIPPED"))
                .andExpect(status().isOk());

        // Only the order's owner can read its shipment - not an arbitrary other customer.
        mockMvc.perform(get("/api/v1/shipments/order/" + orderId).header("Authorization", "Bearer " + tokenFor(intruder)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/shipments/order/" + orderId).header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk());

        // State machine: SHIPPED -> DELIVERED is legal ...
        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));

        // ... but DELIVERED is terminal: no transition back to SHIPPED is allowed.
        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .param("status", "SHIPPED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shipmentStatus_rejectsSkippingStraightToDeliveredAndRejectsCancelledResurrection() throws Exception {
        User owner = createUser("shipstatecustomer@example.com");
        User admin = createAdmin("shipstateadmin@example.com");
        Product product = productWithStock("SHIPSTATE-1", 5);

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());
        String orderResponse = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderResponse).path("data").path("id").asLong();
        String addressResponse = mockMvc.perform(post("/api/v1/shipping-addresses").header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("street", "1 Main St", "city", "Gaza", "country", "PS"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long addressId = objectMapper.readTree(addressResponse).path("data").path("id").asLong();

        String shipmentResponse = mockMvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId, "supplierId", supplier.getId(), "shippingAddressId", addressId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long shipmentId = objectMapper.readTree(shipmentResponse).path("data").path("id").asLong();

        // PENDING -> DELIVERED skips SHIPPED/IN_TRANSIT entirely - illegal.
        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .param("status", "DELIVERED"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk());

        // CANCELLED is terminal - cannot be resurrected into DELIVERED (or anything else).
        mockMvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status").header("Authorization", "Bearer " + tokenFor(admin))
                        .param("status", "DELIVERED"))
                .andExpect(status().isBadRequest());
    }

    // NOTE: an automated multi-threaded regression test for the checkout double-submit race
    // (see OrderService.createOrder's use of CartRepository.findWithLockByUserId) was attempted
    // here and removed. This test class is @Transactional (each test's setup data lives in an
    // uncommitted transaction bound to the main test thread and is rolled back at the end);
    // worker threads spawned for genuine concurrency get their own DB connections/transactions
    // and therefore cannot see that uncommitted setup data, making the race unobservable through
    // this harness rather than proving anything about the fix. The pessimistic-lock fix itself
    // is verified by code review, not by an automated test - documented as a testing gap.

    @Test
    void checkout_appliesCouponDiscountServerSideAndRejectsInvalidCodes() throws Exception {
        User user = createUser("couponuser@example.com");
        Product product = productWithStock("COUPON-1", 10);

        couponRepository.save(Coupon.builder()
                .code("SAVE10").discountPercentage(new java.math.BigDecimal("10.00"))
                .expirationDate(java.time.LocalDateTime.now().plusDays(30)).isActive(true).build());
        couponRepository.save(Coupon.builder()
                .code("EXPIRED").discountPercentage(new java.math.BigDecimal("50.00"))
                .expirationDate(java.time.LocalDateTime.now().minusDays(1)).isActive(true).build());

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 2))))
                .andExpect(status().isOk());

        // Unknown coupon code is rejected rather than silently ignored.
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "DOES-NOT-EXIST"))))
                .andExpect(status().isNotFound());

        // An expired coupon is rejected even though it exists.
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "EXPIRED"))))
                .andExpect(status().isBadRequest());

        // A valid, active coupon reduces the server-computed total - subtotal is 2 x 25.00 = 50.00,
        // 10% off = 45.00. The client never supplies a discount amount, only the code.
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "SAVE10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(45.0))
                .andExpect(jsonPath("$.data.discountAmount").value(5.0))
                .andExpect(jsonPath("$.data.couponCode").value("SAVE10"));
    }

    @Test
    void checkout_enforcesCouponUsageLimitAndMinimumOrder() throws Exception {
        User userA = createUser("couponlimit-a@example.com");
        User userB = createUser("couponlimit-b@example.com");
        User userC = createUser("couponlimit-c@example.com");
        Product product = productWithStock("COUPON-LIMIT-1", 30);

        couponRepository.save(Coupon.builder()
                .code("LIMIT2").discountPercentage(new java.math.BigDecimal("10.00"))
                .expirationDate(java.time.LocalDateTime.now().plusDays(30)).isActive(true)
                .usageLimit(2).timesUsed(0).build());
        couponRepository.save(Coupon.builder()
                .code("BIGORDER").discountPercentage(new java.math.BigDecimal("10.00"))
                .expirationDate(java.time.LocalDateTime.now().plusDays(30)).isActive(true)
                .minimumOrderAmount(new java.math.BigDecimal("1000.00")).build());

        // Minimum order: subtotal (25.00) is far below the coupon's 1000.00 minimum.
        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "BIGORDER"))))
                .andExpect(status().isBadRequest());

        // Usage limit of 2: first two different customers succeed ...
        for (User user : new User[]{userA, userB}) {
            mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "LIMIT2"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.couponCode").value("LIMIT2"));
        }

        // ... a third redemption attempt is rejected once the limit is reached.
        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + tokenFor(userC))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId(), "quantity", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + tokenFor(userC))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingAddress", "1 Main St", "couponCode", "LIMIT2"))))
                .andExpect(status().isBadRequest());

        Coupon persisted = couponRepository.findByCode("LIMIT2").orElseThrow();
        Assertions.assertEquals(2, persisted.getTimesUsed(), "usage counter must reflect exactly the two accepted redemptions, not the rejected third attempt");
    }
}
