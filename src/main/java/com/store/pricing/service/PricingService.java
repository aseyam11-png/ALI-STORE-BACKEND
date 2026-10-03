package com.store.pricing.service;

import com.store.category.repository.CategoryRepository;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.pricing.dto.PricingRuleRequest;
import com.store.pricing.dto.PricingRuleResponse;
import com.store.pricing.entity.PricingRule;
import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import com.store.pricing.repository.PricingRuleRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingRuleRepository pricingRuleRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public List<PricingRuleResponse> getAllRules() {
        return pricingRuleRepository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "id"))
                .stream()
                .map(this::map)
                .toList();
    }

    @Transactional
    public PricingRuleResponse createRule(PricingRuleRequest request) {
        validateScope(request);

        PricingRule rule = PricingRule.builder()
                .scope(request.getScope())
                .type(request.getType())
                .value(request.getValue().setScale(4, RoundingMode.HALF_UP))
                .active(request.getActive() == null || request.getActive())
                .priority(request.getPriority() == null ? 0 : request.getPriority())
                .category(resolveCategory(request.getCategoryId(), request.getScope()))
                .product(resolveProduct(request.getProductId(), request.getScope()))
                .build();

        return map(pricingRuleRepository.save(rule));
    }

    @Transactional
    public PricingRuleResponse updateRule(Long id, PricingRuleRequest request) {
        validateScope(request);

        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found: " + id));

        rule.setScope(request.getScope());
        rule.setType(request.getType());
        rule.setValue(request.getValue().setScale(4, RoundingMode.HALF_UP));
        rule.setActive(request.getActive() == null || request.getActive());
        rule.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        rule.setCategory(resolveCategory(request.getCategoryId(), request.getScope()));
        rule.setProduct(resolveProduct(request.getProductId(), request.getScope()));

        return map(pricingRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long id) {
        if (!pricingRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pricing rule not found: " + id);
        }
        pricingRuleRepository.deleteById(id);
    }

    @Transactional
    public BigDecimal repriceProduct(Product product) {
        if (product.getSupplierCost() == null || product.getSupplierCost().signum() <= 0) {
            throw new BusinessException("Cannot calculate selling price without a valid supplier cost");
        }

        List<PricingRule> rules = pricingRuleRepository.findEffectiveRules(
                product.getCategory().getId(), product.getId());

        if (rules.isEmpty()) {
            return product.getPrice();
        }

        PricingRule rule = rules.get(0);
        BigDecimal sourceCost = product.getSupplierCost();
        BigDecimal sellingPrice;

        if (rule.getType() == PricingRuleType.PERCENTAGE_MARKUP) {
            sellingPrice = sourceCost.add(
                    sourceCost.multiply(rule.getValue())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        } else {
            sellingPrice = sourceCost.add(rule.getValue());
        }

        sellingPrice = sellingPrice.max(sourceCost).setScale(2, RoundingMode.HALF_UP);
        product.setPrice(sellingPrice);
        return sellingPrice;
    }

    @Transactional
    public BigDecimal repriceAndSave(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        BigDecimal price = repriceProduct(product);
        productRepository.save(product);
        return price;
    }

    private void validateScope(PricingRuleRequest request) {
        if (request.getValue() == null || request.getValue().signum() < 0) {
            throw new BusinessException("Pricing rule value cannot be negative");
        }

        switch (request.getScope()) {
            case GLOBAL -> {
                if (request.getCategoryId() != null || request.getProductId() != null) {
                    throw new ConflictException("GLOBAL pricing rules cannot target a category or product");
                }
            }
            case CATEGORY -> {
                if (request.getCategoryId() == null || request.getProductId() != null) {
                    throw new ConflictException("CATEGORY pricing rules require categoryId only");
                }
            }
            case PRODUCT -> {
                if (request.getProductId() == null || request.getCategoryId() != null) {
                    throw new ConflictException("PRODUCT pricing rules require productId only");
                }
            }
        }

        if (request.getType() == PricingRuleType.PERCENTAGE_MARKUP
                && request.getValue().compareTo(new BigDecimal("1000")) > 0) {
            throw new BusinessException("Percentage markup cannot exceed 1000%");
        }
    }

    private com.store.category.entity.Category resolveCategory(Long id, PricingRuleScope scope) {
        if (scope != PricingRuleScope.CATEGORY) return null;
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private Product resolveProduct(Long id, PricingRuleScope scope) {
        if (scope != PricingRuleScope.PRODUCT) return null;
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private PricingRuleResponse map(PricingRule rule) {
        return PricingRuleResponse.builder()
                .id(rule.getId())
                .scope(rule.getScope())
                .type(rule.getType())
                .value(rule.getValue())
                .active(rule.getActive())
                .priority(rule.getPriority())
                .categoryId(rule.getCategory() != null ? rule.getCategory().getId() : null)
                .categoryName(rule.getCategory() != null ? rule.getCategory().getName() : null)
                .productId(rule.getProduct() != null ? rule.getProduct().getId() : null)
                .productName(rule.getProduct() != null ? rule.getProduct().getName() : null)
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
