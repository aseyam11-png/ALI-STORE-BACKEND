package com.store.pricing.service;

import com.store.category.repository.CategoryRepository;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.pricing.dto.PricingRuleRequest;
import com.store.pricing.dto.PricingRuleResponse;
import com.store.pricing.entity.PricingRule;
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
public class PricingRuleService {
    private final PricingRuleRepository pricingRuleRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public List<PricingRuleResponse> findAll() {
        return pricingRuleRepository.findAllByOrderByPriorityDescIdAsc().stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateSellingPrice(Product product) {
        PricingRule rule = null;
        if (product.getId() != null) {
            rule = pricingRuleRepository.findByActiveTrueAndProductIdOrderByPriorityDescIdAsc(product.getId())
                    .stream().findFirst().orElse(null);
        }
        if (rule == null && product.getCategory() != null) {
            rule = pricingRuleRepository.findByActiveTrueAndCategoryIdOrderByPriorityDescIdAsc(product.getCategory().getId())
                    .stream().findFirst().orElse(null);
        }

        if (product.getSupplierCost() == null || product.getSupplierCost().signum() <= 0) {
            throw new BusinessException("Supplier cost is required to calculate a selling price");
        }

        BigDecimal cost = product.getSupplierCost();
        if (rule == null) return money(cost);

        return money(rule.getType() == PricingRuleType.FIXED
                ? cost.add(rule.getValue())
                : cost.multiply(BigDecimal.ONE.add(
                        rule.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))));
    }

    @Transactional
    public PricingRuleResponse create(PricingRuleRequest request) {
        validateScope(request);
        PricingRule rule = PricingRule.builder()
                .name(request.getName().trim())
                .type(request.getType())
                .value(request.getValue())
                .active(request.getActive())
                .priority(request.getPriority())
                .product(request.getProductId() == null ? null : productRepository.findById(request.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId())))
                .category(request.getCategoryId() == null ? null : categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId())))
                .build();
        return map(pricingRuleRepository.save(rule));
    }

    @Transactional
    public PricingRuleResponse update(Long id, PricingRuleRequest request) {
        validateScope(request);
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found with id: " + id));

        rule.setName(request.getName().trim());
        rule.setType(request.getType());
        rule.setValue(request.getValue());
        rule.setActive(request.getActive());
        rule.setPriority(request.getPriority());
        rule.setProduct(request.getProductId() == null ? null : productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId())));
        rule.setCategory(request.getCategoryId() == null ? null : categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId())));
        return map(pricingRuleRepository.save(rule));
    }

    @Transactional
    public void delete(Long id) {
        if (!pricingRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pricing rule not found with id: " + id);
        }
        pricingRuleRepository.deleteById(id);
    }

    private void validateScope(PricingRuleRequest request) {
        if (request.getProductId() != null && request.getCategoryId() != null) {
            throw new BusinessException("A pricing rule must target either a product or a category, not both");
        }
    }

    private PricingRuleResponse map(PricingRule rule) {
        return PricingRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .productId(rule.getProduct() == null ? null : rule.getProduct().getId())
                .categoryId(rule.getCategory() == null ? null : rule.getCategory().getId())
                .type(rule.getType())
                .value(rule.getValue())
                .active(rule.getActive())
                .priority(rule.getPriority())
                .build();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}