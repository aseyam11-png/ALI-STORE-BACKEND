package com.store.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.service.JwtService;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.inventory.entity.Inventory;
import com.store.inventory.repository.InventoryRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Foundational, non-negotiable behaviors for Phase 1 stabilization:
 * auth issues real tokens, the catalog foundation (category/product/inventory)
 * is wired end to end, and customer-owned resources cannot be accessed by
 * spoofing identity via headers/params instead of the authenticated principal.
 * Runs against an isolated in-memory H2 database (see application-test.yml) -
 * see the stabilization report for why Flyway-against-real-Postgres is
 * validated separately.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private InventoryRepository inventoryRepository;

    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void seedRoles() {
        userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("Standard customer role").build()));
        adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Administrator role").build()));
    }

    private User createUser(String email, Role role) {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .password(passwordEncoder.encode("Password123"))
                .phoneNumber("0000000000")
                .isEnabled(true)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getName())));
        return jwtService.generateToken(userDetails);
    }

    @Test
    void registerThenLogin_issuesRealJwt() throws Exception {
        Map<String, String> registerBody = Map.of(
                "firstName", "Ali", "lastName", "Store", "email", "ali@example.com",
                "password", "Password123", "phoneNumber", "0599000000");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().exists("ali_store_session"));

        Map<String, String> loginBody = Map.of("email", "ali@example.com", "password", "Password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().exists("ali_store_session"));
    }

    @Test
    void register_rejectsPasswordBelowMinimumLength() throws Exception {
        Map<String, String> body = Map.of(
                "firstName", "Ali", "lastName", "Store", "email", "shortpw@example.com",
                "password", "Short1", "phoneNumber", "0599000000");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_rejectsPasswordOverBcryptByteLimit_evenWhenUnderCharacterLimit() throws Exception {
        // 36 Arabic letters (2 UTF-8 bytes each = 72 bytes) + 2 more ASCII chars = 74 bytes,
        // but only 38 CHARACTERS - well under a naive 72-character limit. A byte-blind
        // validator (e.g. plain @Size) would wrongly accept this and let it silently
        // truncate under BCrypt's 72-byte limit.
        String password = "ا".repeat(36) + "A1";
        Map<String, String> body = Map.of(
                "firstName", "Ali", "lastName", "Store", "email", "unicodepw@example.com",
                "password", password, "phoneNumber", "0599000000");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_acceptsPasswordWithinByteLimit() throws Exception {
        Map<String, String> body = Map.of(
                "firstName", "Ali", "lastName", "Store", "email", "validbytepw@example.com",
                "password", "Password123", "phoneNumber", "0599000000");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void me_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void me_withToken_returnsCurrentUserWithoutPassword() throws Exception {
        User user = createUser("me@example.com", userRole);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void categoryCreation_isAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());

        Map<String, String> body = Map.of("name", "Electronics");

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());

        User regular = createUser("shopper@example.com", userRole);
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(regular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        User admin = createUser("admin@example.com", adminRole);
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("electronics"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void productCreation_exposesCanonicalFieldsAndInitializesInventory() throws Exception {
        User admin = createUser("catalog-admin@example.com", adminRole);
        Category category = categoryRepository.save(Category.builder()
                .name("Phones").slug("phones").active(true).build());
        Supplier supplier = supplierRepository.findByExternalCode("TEST-SUPPLIER")
                .orElseGet(() -> supplierRepository.save(
                        Supplier.builder().externalCode("TEST-SUPPLIER").name("Test Supplier").build()));

        Map<String, Object> body = Map.of(
                "name", "Test Phone", "price", 199.99, "sku", "PHN-001",
                "categoryId", category.getId(),
                "supplierId", supplier.getId(),
                "supplierSku", "SUP-PHN-001",
                "supplierCost", 100.00);

        String response = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("test-phone"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        Product saved = productRepository.findBySku("PHN-001").orElseThrow();
        Inventory inventory = inventoryRepository.findByProductId(saved.getId()).orElseThrow();
        Assertions.assertEquals(0, inventory.getQuantity());

        mockMvc.perform(get("/api/v1/inventory/product/" + saved.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("UNKNOWN"));
    }

    @Test
    void shippingAddressOwnership_isEnforcedAgainstAuthenticatedPrincipal() throws Exception {
        User owner = createUser("owner@example.com", userRole);
        User intruder = createUser("intruder@example.com", userRole);

        Map<String, String> address = Map.of("street", "123 Main St", "city", "Gaza", "country", "PS");

        String response = mockMvc.perform(post("/api/v1/shipping-addresses")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(address)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long addressId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/shipping-addresses/" + addressId)
                        .header("Authorization", "Bearer " + tokenFor(intruder)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/shipping-addresses/" + addressId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void wishlist_ignoresClientSuppliedIdentityHeader() throws Exception {
        mockMvc.perform(get("/api/v1/wishlists"))
                .andExpect(status().is4xxClientError());

        // A spoofed X-User-Id header must not grant access - only the authenticated principal counts.
        mockMvc.perform(get("/api/v1/wishlists").header("X-User-Id", "1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void responses_carryExplicitSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Referrer-Policy", "no-referrer"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=(), payment=()"));
    }

    @Test
    void legacyB2BEndpoints_areRemoved() throws Exception {
        User regular = createUser("quotationcustomer@example.com", userRole);

        mockMvc.perform(post("/api/v1/quotations")
                        .header("Authorization", "Bearer " + tokenFor(regular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rfqId", 1, "sellerId", 1, "totalPrice", 10.00))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/rfqs")
                        .header("Authorization", "Bearer " + tokenFor(regular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/sellers")
                        .header("Authorization", "Bearer " + tokenFor(regular)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/b2b")
                        .header("Authorization", "Bearer " + tokenFor(regular)))
                .andExpect(status().isNotFound());
    }

    @Test
    void supplierWebhook_requiresItsDedicatedSecret() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/suppliers/shipments/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "supplierCode", "TEST-SUPPLIER",
                                "supplierOrderId", "SUP-ORDER-1",
                                "status", "IN_TRANSIT"))))
                .andExpect(status().isUnauthorized());
    }

}
