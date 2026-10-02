package com.store.pricing.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.pricing.dto.PricingRuleRequest;
import com.store.pricing.dto.PricingRuleResponse;
import com.store.pricing.entity.PricingRule;
import com.store.pricing.repository.PricingRuleRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingRuleService {
    private final PricingRuleRepository repository;
    private final ProductRepository productRepository;

    @Value("${app.pricing.default-markup-percent:20}")
    private BigDecimal defaultMarkupPercent;

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> getAll() {
        return repository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "priority"))
                .stream().map(this::map).toList();
    }

    @Transactional
    public PricingRuleResponse create(PricingRuleRequest request) {
        validateScope(request.getProductId(), request.getCategoryId());
        PricingRule rule = PricingRule.builder()
                .name(request.getName().trim())
                .productId(request.getProductId())
                .categoryId(request.getCategoryId())
                .type(request.getType())
                .value(nonNegative(request.getValue()))
                .active(request.getActive() == null || request.getActive())
                .priority(request.getPriority() == null ? 0 : request.getPriority())
                .build();
        return map(repository.save(rule));
    }

    @Transactional
    public PricingRuleResponse update(Long id, PricingRuleRequest request) {
        validateScope(request.getProductId(), request.getCategoryId());
        PricingRule rule = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found"));
        rule.setName(request.getName().trim());
        rule.setProductId(request.getProductId());
        rule.setCategoryId(request.getCategoryId());
        rule.setType(request.getType());
        rule.setValue(nonNegative(request.getValue()));
        rule.setActive(request.getActive() == null || request.getActive());
        rule.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        return map(repository.save(rule));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Pricing rule not found");
        repository.deleteById(id);
    }

    @Transactional
    public BigDecimal recalculateProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getSupplierCost() == null || product.getSupplierCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Product has no supplier cost to calculate from");
        }
        product.setPrice(apply(product.getSupplierCost(), resolveRule(product)));
        productRepository.save(product);
        return product.getPrice();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculate(BigDecimal supplierCost, Long productId, Long categoryId) {
        if (supplierCost == null || supplierCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Supplier cost must be greater than zero");
        }
        PricingRule rule = repository.findByActiveTrueOrderByPriorityDesc().stream()
                .filter(item -> (productId != null && productId.equals(item.getProductId()))
                        || (item.getProductId() == null && categoryId != null && categoryId.equals(item.getCategoryId())))
                .sorted(Comparator.comparingInt((PricingRule item) -> item.getProductId() != null ? 2 : 1)
                        .thenComparingInt(PricingRule::getPriority).reversed())
                .findFirst().orElse(null);
        return apply(supplierCost, rule);
    }

    private PricingRule resolveRule(Product product) {
        return repository.findByActiveTrueOrderByPriorityDesc().stream()
                .filter(item -> (item.getProductId() != null && item.getProductId().equals(product.getId()))
                        || (item.getProductId() == null && item.getCategoryId() != null
                        && product.getCategory() != null && item.getCategoryId().equals(product.getCategory().getId())))
                .sorted(Comparator.comparingInt((PricingRule item) -> item.getProductId() != null ? 2 : 1)
                        .thenComparingInt(PricingRule::getPriority).reversed())
                .findFirst().orElse(null);
    }

    private BigDecimal apply(BigDecimal supplierCost, PricingRule rule) {
        BigDecimal price;
        if (rule == null) {
            price = supplierCost.multiply(BigDecimal.ONE.add(nonNegative(defaultMarkupPercent)
                    .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        } else if (rule.getType() == PricingRule.RuleType.FIXED) {
            price = supplierCost.add(rule.getValue());
        } else {
            price = supplierCost.multiply(BigDecimal.ONE.add(rule.getValue()
                    .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        }
        return price.max(supplierCost).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateScope(Long productId, Long categoryId) {
        if (productId != null && categoryId != null) {
            throw new BusinessException("A pricing rule must target either a product or a category, not both");
        }
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    private PricingRuleResponse map(PricingRule rule) {
        return PricingRuleResponse.builder()
                .id(rule.getId()).name(rule.getName()).productId(rule.getProductId()).categoryId(rule.getCategoryId())
                .type(rule.getType()).value(rule.getValue()).active(rule.isActive()).priority(rule.getPriority()).build();
    }
}
