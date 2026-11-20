package com.store.foundation;

import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.pricing.entity.PricingRule;
import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import com.store.pricing.repository.PricingRuleRepository;
import com.store.pricing.service.PricingService;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PricingPolicyTest {

    @Autowired private PricingService pricingService;
    @Autowired private PricingRuleRepository pricingRuleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SupplierRepository supplierRepository;

    private Category category;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(Category.builder()
                .name("Pricing Test").slug("pricing-test").active(true).build());
        supplier = supplierRepository.save(Supplier.builder()
                .externalCode("PRICING-SUPPLIER").name("Pricing Supplier")
                .status(com.store.supplier.entity.SupplierStatus.ACTIVE)
                .fulfillmentEnabled(true).inventorySyncEnabled(true).build());
    }

    private Product product(String sku) {
        return productRepository.save(Product.builder()
                .name("Pricing Product")
                .slug("pricing-" + sku.toLowerCase())
                .price(new BigDecimal("100.00"))
                .currency("USD")
                .sku(sku)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .supplier(supplier)
                .supplierSku("SUP-" + sku)
                .supplierCost(new BigDecimal("50.00"))
                .build());
    }

    @Test
    void productRuleOverridesCategoryRuleAndCalculatesFromSupplierCost() {
        Product product = product("PRICE-001");

        pricingRuleRepository.save(PricingRule.builder()
                .scope(PricingRuleScope.CATEGORY).type(PricingRuleType.PERCENTAGE_MARKUP)
                .value(new BigDecimal("20.00")).priority(10).active(true).category(category).build());
        pricingRuleRepository.save(PricingRule.builder()
                .scope(PricingRuleScope.PRODUCT).type(PricingRuleType.FIXED_MARKUP)
                .value(new BigDecimal("15.00")).priority(0).active(true).product(product).build());

        BigDecimal result = pricingService.repriceProduct(product);

        assertEquals(new BigDecimal("65.00"), result);
        assertEquals(new BigDecimal("65.00"), product.getPrice());
    }

    @Test
    void noPricingRuleKeepsCurrentStorePriceInsteadOfInventingAMarkup() {
        Product product = product("PRICE-002");
        BigDecimal result = pricingService.repriceProduct(product);
        assertEquals(new BigDecimal("100.00"), result);
        assertEquals(new BigDecimal("100.00"), product.getPrice());
    }
}
