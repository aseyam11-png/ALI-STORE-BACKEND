package com.store.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.service.JwtService;
import com.store.brand.entity.Brand;
import com.store.brand.repository.BrandRepository;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog invariants (unique slug/SKU, invalid category rejection, DRAFT vs
 * ARCHIVED vs ACTIVE visibility) and JWT robustness against malformed/tampered
 * tokens (must degrade to a clean 4xx, never a 500 - see JwtAuthenticationFilter).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;

    private Role adminRole;
    private Category category;

    @BeforeEach
    void setUp() {
        roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("Standard customer role").build()));
        adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Administrator role").build()));
        category = categoryRepository.save(Category.builder().name("Electronics").slug("electronics").active(true).build());
    }

    private String adminToken() {
        User admin = userRepository.save(User.builder()
                .firstName("Admin").lastName("User").email("admin-" + System.nanoTime() + "@example.com")
                .password(passwordEncoder.encode("Password123")).phoneNumber("0").isEnabled(true).role(adminRole).build());
        UserDetails ud = new org.springframework.security.core.userdetails.User(
                admin.getEmail(), admin.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(admin.getRole().getName())));
        return jwtService.generateToken(ud);
    }

    @Test
    void malformedJwt_degradesToCleanRejection_notServerError() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void tamperedSignature_degradesToCleanRejection_notServerError() throws Exception {
        String realToken = adminToken();
        String tampered = realToken.substring(0, realToken.length() - 4) + "abcd";
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void productCreation_rejectsDuplicateSku() throws Exception {
        String token = adminToken();
        Map<String, Object> body = Map.of("name", "Phone A", "price", 100, "sku", "DUP-SKU", "categoryId", category.getId());

        mockMvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Map<String, Object> duplicate = Map.of("name", "Phone B", "price", 150, "sku", "DUP-SKU", "categoryId", category.getId());
        mockMvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void productCreation_rejectsInvalidCategory() throws Exception {
        String token = adminToken();
        Map<String, Object> body = Map.of("name", "Orphan Product", "price", 50, "sku", "ORPHAN-1", "categoryId", 999999L);

        mockMvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryCreation_rejectsCircularParent() throws Exception {
        String token = adminToken();
        Category child = categoryRepository.save(Category.builder().name("Phones").slug("phones").active(true).parent(category).build());

        // Trying to make the parent's parent be its own child must be rejected.
        Map<String, Object> body = Map.of("parentId", child.getId());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/categories/" + category.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archivedProduct_hiddenFromListing_and_notResolvableById() throws Exception {
        Product active = productRepository.save(Product.builder()
                .name("Active One").slug("active-one").price(BigDecimal.TEN).currency("USD").sku("ACT-1")
                .status(ProductStatus.ACTIVE).category(category).build());
        Product archived = productRepository.save(Product.builder()
                .name("Archived One").slug("archived-one").price(BigDecimal.TEN).currency("USD").sku("ARC-1")
                .status(ProductStatus.ARCHIVED).category(category).build());
        Product draft = productRepository.save(Product.builder()
                .name("Draft One").slug("draft-one").price(BigDecimal.TEN).currency("USD").sku("DRF-1")
                .status(ProductStatus.DRAFT).category(category).build());

        // Listing only ever contains ACTIVE products.
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku=='ARC-1')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.sku=='DRF-1')]").isEmpty());

        // Archived products are intentionally not customer-resolvable; order history
        // keeps its own historical snapshot and does not depend on catalog visibility.
        mockMvc.perform(get("/api/v1/products/" + archived.getId()))
                .andExpect(status().isNotFound());

        // Draft is never visible to a customer, even by direct id.
        mockMvc.perform(get("/api/v1/products/" + draft.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/products/" + active.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void productSearch_rejectsUnsupportedSort() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "rating_desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productSearch_rejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("pageSize", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productSearch_filtersByPriceRange() throws Exception {
        productRepository.save(Product.builder().name("Cheap").slug("cheap-x").price(new BigDecimal("10.00"))
                .currency("USD").sku("CHEAP-1").status(ProductStatus.ACTIVE).category(category).build());
        productRepository.save(Product.builder().name("Expensive").slug("expensive-x").price(new BigDecimal("500.00"))
                .currency("USD").sku("EXP-1").status(ProductStatus.ACTIVE).category(category).build());

        mockMvc.perform(get("/api/v1/products").param("minPrice", "100").param("maxPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku=='CHEAP-1')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.sku=='EXP-1')]").exists());
    }
}
