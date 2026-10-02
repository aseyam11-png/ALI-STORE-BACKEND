package com.store.pricing.service;

import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.pricing.entity.PricingRule;
import com.store.pricing.entity.PricingRuleType;
import com.store.pricing.repository.PricingRuleRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingRuleServiceTest {

    @Mock private PricingRuleRepository pricingRuleRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private PricingRuleService pricingRuleService;

    @Test
    void productRule_hasPriorityOverCategoryRule() {
        Product product = Product.builder()
                .supplierCost(new BigDecimal("100.00"))
                .build();
        product.setId(7L);

        Category category = Category.builder().name("Fashion").slug("fashion").build();
        category.setId(3L);
        product.setCategory(category);

        PricingRule productRule = PricingRule.builder()
                .type(PricingRuleType.FIXED)
                .value(new BigDecimal("20"))
                .active(true)
                .priority(100)
                .product(product)
                .build();

        PricingRule categoryRule = PricingRule.builder()
                .type(PricingRuleType.PERCENTAGE)
                .value(new BigDecimal("50"))
                .active(true)
                .priority(10)
                .category(category)
                .build();

        when(pricingRuleRepository.findByActiveTrueAndProductIdOrderByPriorityDescIdAsc(7L))
                .thenReturn(List.of(productRule));
        assertEquals(new BigDecimal("120.00"), pricingRuleService.calculateSellingPrice(product));
    }

    @Test
    void percentageRule_calculatesFromSupplierCost() {
        Product product = Product.builder()
                .supplierCost(new BigDecimal("80.00"))
                .build();
        product.setId(11L);

        Category category = Category.builder().name("Beauty").slug("beauty-care").build();
        category.setId(8L);
        product.setCategory(category);

        PricingRule categoryRule = PricingRule.builder()
                .type(PricingRuleType.PERCENTAGE)
                .value(new BigDecimal("25"))
                .active(true)
                .priority(10)
                .category(category)
                .build();

        when(pricingRuleRepository.findByActiveTrueAndProductIdOrderByPriorityDescIdAsc(11L))
                .thenReturn(List.of());
        when(pricingRuleRepository.findByActiveTrueAndCategoryIdOrderByPriorityDescIdAsc(8L))
                .thenReturn(List.of(categoryRule));

        assertEquals(new BigDecimal("100.00"), pricingRuleService.calculateSellingPrice(product));
    }
}
