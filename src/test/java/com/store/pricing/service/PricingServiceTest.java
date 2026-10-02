package com.store.pricing.service;

import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.pricing.entity.PricingRule;
import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import com.store.pricing.repository.PricingRuleRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pricingService = new PricingService(
                pricingRuleRepository,
                productRepository,
                categoryRepository);
    }

    @Test
    void calculateSellingPrice_appliesFixedCategoryMarkup() {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(10L);
        Product product = Product.builder()
                .supplierCost(new BigDecimal("40.00"))
                .category(category)
                .build();

        PricingRule rule = PricingRule.builder()
                .scope(PricingRuleScope.CATEGORY)
                .type(PricingRuleType.FIXED)
                .value(new BigDecimal("10.00"))
                .priority(10)
                .active(true)
                .category(category)
                .build();

        when(pricingRuleRepository.findByActiveTrueOrderByPriorityDescIdAsc())
                .thenReturn(List.of(rule));

        assertEquals(
                new BigDecimal("50.00"),
                pricingService.calculateSellingPrice(product));
    }

    @Test
    void calculateSellingPrice_prefersProductRuleOverCategoryRuleAtSamePriority() {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(10L);
        Product product = mock(Product.class);
        when(product.getSupplierCost()).thenReturn(new BigDecimal("100.00"));
        when(product.getCategory()).thenReturn(category);
        when(product.getId()).thenReturn(100L);

        PricingRule categoryRule = PricingRule.builder()
                .scope(PricingRuleScope.CATEGORY)
                .type(PricingRuleType.PERCENTAGE)
                .value(new BigDecimal("10.00"))
                .priority(10)
                .active(true)
                .category(category)
                .build();

        PricingRule productRule = PricingRule.builder()
                .scope(PricingRuleScope.PRODUCT)
                .type(PricingRuleType.FIXED)
                .value(new BigDecimal("25.00"))
                .priority(10)
                .active(true)
                .product(product)
                .build();

        when(pricingRuleRepository.findByActiveTrueOrderByPriorityDescIdAsc())
                .thenReturn(List.of(categoryRule, productRule));

        assertEquals(
                new BigDecimal("125.00"),
                pricingService.calculateSellingPrice(product));
    }
}
