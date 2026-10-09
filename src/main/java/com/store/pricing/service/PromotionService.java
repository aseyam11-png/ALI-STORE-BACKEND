package com.store.pricing.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.pricing.dto.PromotionRequest;
import com.store.pricing.dto.PromotionResponse;
import com.store.pricing.entity.Promotion;
import com.store.pricing.entity.PromotionType;
import com.store.pricing.repository.PromotionRepository;
import com.store.product.entity.Product;
import com.store.product.entity.ProductVariant;
import com.store.product.repository.ProductRepository;
import com.store.inventory.enums.PublicAvailability;
import com.store.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionService {
    private final PromotionRepository repository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public Promotion findActiveForProduct(Long productId) {
        return repository.findActiveForProduct(productId, LocalDateTime.now()).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public PriceResult calculate(Product product, ProductVariant variant) {
        BigDecimal base = variant != null && variant.getPrice() != null ? variant.getPrice() : product.getPrice();
        Promotion promotion = findActiveForProduct(product.getId());
        if (promotion == null) return PriceResult.full(base, null);
        BigDecimal discount = promotion.getType() == PromotionType.PERCENTAGE
                ? base.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        discount = discount.max(BigDecimal.ZERO).min(base);
        BigDecimal effective = base.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent = base.signum() == 0
                ? BigDecimal.ZERO
                : discount.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
        return new PriceResult(base.setScale(2, RoundingMode.HALF_UP), effective, discount, percent, promotion);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> getActivePromotions(boolean flashOnly) {
        return repository.findActive(LocalDateTime.now()).stream()
                .filter(p -> !flashOnly || Boolean.TRUE.equals(p.getFlashSale()))
                .collect(java.util.stream.Collectors.toMap(
                        p -> p.getProduct().getId(),
                        p -> p,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
                .map(this::map)
                .toList();
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        validate(request);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        Promotion promotion = Promotion.builder()
                .product(product).title(request.getTitle().trim()).type(request.getType())
                .discountValue(request.getDiscountValue().setScale(2, RoundingMode.HALF_UP))
                .startsAt(request.getStartsAt()).endsAt(request.getEndsAt())
                .active(request.getActive() == null || request.getActive())
                .flashSale(request.getFlashSale() != null && request.getFlashSale())
                .priority(request.getPriority() == null ? 0 : request.getPriority()).build();
        return map(repository.save(promotion));
    }

    @Transactional
    public PromotionResponse update(Long id, PromotionRequest request) {
        validate(request);
        Promotion promotion = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + id));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        promotion.setProduct(product); promotion.setTitle(request.getTitle().trim()); promotion.setType(request.getType());
        promotion.setDiscountValue(request.getDiscountValue().setScale(2, RoundingMode.HALF_UP));
        promotion.setStartsAt(request.getStartsAt()); promotion.setEndsAt(request.getEndsAt());
        promotion.setActive(request.getActive() == null || request.getActive());
        promotion.setFlashSale(request.getFlashSale() != null && request.getFlashSale());
        promotion.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        return map(repository.save(promotion));
    }

    @Transactional
    public void delete(Long id) {
        Promotion promotion = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + id));
        promotion.setActive(false);
        repository.save(promotion);
    }

    private void validate(PromotionRequest request) {
        if (request.getStartsAt() == null || request.getEndsAt() == null || !request.getEndsAt().isAfter(request.getStartsAt()))
            throw new BusinessException("Promotion dates are invalid");
        if (request.getType() == PromotionType.PERCENTAGE
                && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new BusinessException("Percentage promotion cannot exceed 100%");
    }

    private PromotionResponse map(Promotion promotion) {
        BigDecimal original = promotion.getProduct().getPrice();
        PriceResult result = calculatePromotion(promotion, promotion.getProduct().getPrice());
        String image = promotion.getProduct().getImages().stream()
                .filter(com.store.product.entity.ProductImage::getIsPrimary)
                .map(com.store.product.entity.ProductImage::getUrl).findFirst().orElse(null);
        return PromotionResponse.builder().id(promotion.getId()).productId(promotion.getProduct().getId())
                .productName(promotion.getProduct().getName()).productImageUrl(image).title(promotion.getTitle())
                .type(promotion.getType()).discountValue(promotion.getDiscountValue()).originalPrice(original)
                .price(result.effectivePrice()).discountPercentage(result.discountPercentage())
                .currency(promotion.getProduct().getCurrency())
                .availability(inventoryService.getPublicAvailability(promotion.getProduct().getId()))
                .availableQuantity(inventoryService.getAvailableQuantity(promotion.getProduct().getId()))
                .startsAt(promotion.getStartsAt())
                .endsAt(promotion.getEndsAt()).active(promotion.getActive()).flashSale(promotion.getFlashSale())
                .priority(promotion.getPriority()).build();
    }

    private PriceResult calculatePromotion(Promotion promotion, BigDecimal base) {
        BigDecimal normalizedBase = base == null
                ? BigDecimal.ZERO
                : base.setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = promotion.getType() == PromotionType.PERCENTAGE
                ? normalizedBase.multiply(promotion.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();

        discount = discount.max(BigDecimal.ZERO).min(normalizedBase);
        BigDecimal effective = normalizedBase.subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent = normalizedBase.signum() == 0
                ? BigDecimal.ZERO
                : discount.multiply(BigDecimal.valueOf(100))
                    .divide(normalizedBase, 2, RoundingMode.HALF_UP);

        return new PriceResult(normalizedBase, effective, discount, percent, promotion);
    }

    public record PriceResult(BigDecimal originalPrice, BigDecimal effectivePrice, BigDecimal discountAmount,
                              BigDecimal discountPercentage, Promotion promotion) {
        static PriceResult full(BigDecimal base, Promotion promotion) {
            BigDecimal normalized = base == null ? BigDecimal.ZERO : base.setScale(2, RoundingMode.HALF_UP);
            return new PriceResult(normalized, normalized, BigDecimal.ZERO, BigDecimal.ZERO, promotion);
        }
    }
}