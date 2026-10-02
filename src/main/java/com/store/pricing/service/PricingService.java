package com.store.pricing.service;

import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.response.PageResponse;
import com.store.pricing.dto.PricingRuleRequest;
import com.store.pricing.dto.PricingRuleResponse;
import com.store.pricing.entity.PricingRule;
import com.store.pricing.entity.PricingRuleScope;
import com.store.pricing.entity.PricingRuleType;
import com.store.pricing.repository.PricingRuleRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public PageResponse<PricingRuleResponse> getAll(Pageable pageable) {
        Page<PricingRuleResponse> page = pricingRuleRepository.findAll(pageable).map(this::map);
        return PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    @Transactional
    public PricingRuleResponse create(PricingRuleRequest request) {
        validateTarget(request);

        PricingRule rule = PricingRule.builder()
                .name(request.getName().trim())
                .scope(request.getScope())
                .type(request.getType())
                .value(normalizeValue(request.getValue()))
                .priority(request.getPriority())
                .active(request.getActive() == null || request.getActive())
                .product(resolveProduct(request))
                .category(resolveCategory(request))
                .build();

        return map(pricingRuleRepository.save(rule));
    }

    @Transactional
    public PricingRuleResponse update(Long id, PricingRuleRequest request) {
        validateTarget(request);

        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found with id: " + id));

        rule.setName(request.getName().trim());
        rule.setScope(request.getScope());
        rule.setType(request.getType());
        rule.setValue(normalizeValue(request.getValue()));
        rule.setPriority(request.getPriority());
        if (request.getActive() != null) rule.setActive(request.getActive());
        rule.setProduct(resolveProduct(request));
        rule.setCategory(resolveCategory(request));

        return map(pricingRuleRepository.save(rule));
    }

    @Transactional
    public void delete(Long id) {
        if (!pricingRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pricing rule not found with id: " + id);
        }
        pricingRuleRepository.deleteById(id);
    }

    public BigDecimal calculateSellingPrice(Product product) {
        if (product.getSupplierCost() == null || product.getSupplierCost().signum() <= 0) {
            throw new BusinessException("Supplier cost must be set before automatic pricing");
        }

        PricingRule rule = pricingRuleRepository.findByActiveTrueOrderByPriorityDescIdAsc().stream()
                .filter(candidate -> matches(candidate, product))
                .sorted(
                        Comparator
                                .comparing((PricingRule candidate) -> candidate.getScope() == PricingRuleScope.PRODUCT ? 0 : 1)
                                .thenComparing(PricingRule::getPriority, Comparator.reverseOrder())
                                .thenComparing(PricingRule::getId, Comparator.nullsLast(Long::compareTo)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No active pricing rule matches this product"));

        BigDecimal supplierCost = product.getSupplierCost();
        BigDecimal sellingPrice = switch (rule.getType()) {
            case FIXED -> supplierCost.add(rule.getValue());
            case PERCENTAGE -> supplierCost.add(
                    supplierCost.multiply(rule.getValue()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        };

        if (sellingPrice.compareTo(supplierCost) < 0) {
            throw new BusinessException("Automatic selling price cannot be lower than supplier cost");
        }

        return sellingPrice.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSellingPricePreview(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return calculateSellingPrice(product);
    }

    private boolean matches(PricingRule rule, Product product) {
        if (rule.getScope() == PricingRuleScope.PRODUCT) {
            return rule.getProduct() != null && rule.getProduct().getId().equals(product.getId());
        }
        return rule.getCategory() != null
                && product.getCategory() != null
                && rule.getCategory().getId().equals(product.getCategory().getId());
    }

    private void validateTarget(PricingRuleRequest request) {
        if (request.getScope() == PricingRuleScope.PRODUCT) {
            if (request.getProductId() == null || request.getCategoryId() != null) {
                throw new BusinessException("Product pricing rules require productId only");
            }
        } else if (request.getScope() == PricingRuleScope.CATEGORY) {
            if (request.getCategoryId() == null || request.getProductId() != null) {
                throw new BusinessException("Category pricing rules require categoryId only");
            }
        }
        if (request.getPriority() == null || request.getPriority() < 0) {
            throw new BusinessException("Priority must be >= 0");
        }
    }

    private Product resolveProduct(PricingRuleRequest request) {
        return request.getScope() == PricingRuleScope.PRODUCT
                ? productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()))
                : null;
    }

    private Category resolveCategory(PricingRuleRequest request) {
        return request.getScope() == PricingRuleScope.CATEGORY
                ? categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()))
                : null;
    }

    private BigDecimal normalizeValue(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException("Pricing rule value must be >= 0");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private PricingRuleResponse map(PricingRule rule) {
        return PricingRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .scope(rule.getScope())
                .productId(rule.getProduct() == null ? null : rule.getProduct().getId())
                .productName(rule.getProduct() == null ? null : rule.getProduct().getName())
                .categoryId(rule.getCategory() == null ? null : rule.getCategory().getId())
                .categoryName(rule.getCategory() == null ? null : rule.getCategory().getName())
                .type(rule.getType())
                .value(rule.getValue())
                .priority(rule.getPriority())
                .active(rule.getActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
