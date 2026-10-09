package com.store.product.service;

import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.product.dto.ProductVariantRequest;
import com.store.product.dto.ProductVariantAdminResponse;
import com.store.product.dto.ProductVariantResponse;
import com.store.product.entity.Product;
import com.store.product.entity.ProductVariant;
import com.store.product.repository.ProductRepository;
import com.store.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getPublicVariants(Long productId) {
        requireProduct(productId);
        return variantRepository.findByProductIdAndActiveTrueOrderByIdAsc(productId).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductVariantAdminResponse> getAdminVariants(Long productId) {
        requireProduct(productId);
        return variantRepository.findByProductIdOrderByIdAsc(productId).stream().map(this::mapAdmin).toList();
    }

    @Transactional
    public ProductVariantAdminResponse create(Long productId, ProductVariantRequest request) {
        Product product = requireProduct(productId);
        String sku = request.getSku().trim();
        String optionName = request.getOptionName().trim();
        String optionValue = request.getOptionValue().trim();
        if (variantRepository.existsBySku(sku)) throw new ConflictException("Variant SKU already exists: " + sku);
        if (variantRepository.findByProductIdOrderByIdAsc(productId).stream().anyMatch(v ->
                v.getOptionName().equalsIgnoreCase(optionName) && v.getOptionValue().equalsIgnoreCase(optionValue))) {
            throw new ConflictException("This variant option already exists for the product");
        }
        validateSupplierPricing(product, request.getPrice(), request.getSupplierCost());
        ProductVariant variant = ProductVariant.builder().product(product).sku(sku)
                .supplierSku(request.getSupplierSku().trim()).supplierCost(request.getSupplierCost())
                .optionName(optionName).optionValue(optionValue).price(request.getPrice())
                .availableQuantity(request.getAvailableQuantity() == null ? 0 : request.getAvailableQuantity())
                .imageUrl(normalize(request.getImageUrl()))
                .active(request.getActive() == null || request.getActive()).build();
        return mapAdmin(variantRepository.save(variant));
    }

    @Transactional
    public ProductVariantAdminResponse update(Long productId, Long variantId, ProductVariantRequest request) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        String sku = request.getSku().trim();
        variantRepository.findBySku(sku).filter(v -> !v.getId().equals(variantId)).ifPresent(v -> {
            throw new ConflictException("Variant SKU already exists: " + sku);
        });
        String optionName = request.getOptionName().trim();
        String optionValue = request.getOptionValue().trim();
        variantRepository.findByProductIdOrderByIdAsc(productId).stream()
                .filter(v -> !v.getId().equals(variantId))
                .filter(v -> v.getOptionName().equalsIgnoreCase(optionName) && v.getOptionValue().equalsIgnoreCase(optionValue))
                .findFirst().ifPresent(v -> { throw new ConflictException("This variant option already exists for the product"); });
        validateSupplierPricing(variant.getProduct(), request.getPrice(), request.getSupplierCost());
        variant.setSku(sku);
        variant.setSupplierSku(request.getSupplierSku().trim());
        variant.setSupplierCost(request.getSupplierCost());
        variant.setOptionName(optionName); variant.setOptionValue(optionValue);
        variant.setPrice(request.getPrice()); variant.setAvailableQuantity(request.getAvailableQuantity() == null ? 0 : request.getAvailableQuantity());
        variant.setImageUrl(normalize(request.getImageUrl())); variant.setActive(request.getActive() == null || request.getActive());
        return mapAdmin(variantRepository.save(variant));
    }

    @Transactional
    public void delete(Long productId, Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        variant.setActive(false);
        variantRepository.save(variant);
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private void validateSupplierPricing(Product product, java.math.BigDecimal variantPrice,
                                         java.math.BigDecimal variantSupplierCost) {
        if (variantSupplierCost != null && variantSupplierCost.signum() <= 0) {
            throw new com.store.common.exception.BusinessException("Variant supplier cost must be greater than zero");
        }
        java.math.BigDecimal cost = variantSupplierCost != null ? variantSupplierCost : product.getSupplierCost();
        java.math.BigDecimal sellingPrice = variantPrice != null ? variantPrice : product.getPrice();
        if (cost != null && sellingPrice.compareTo(cost) < 0) {
            throw new com.store.common.exception.BusinessException("Variant selling price cannot be lower than supplier cost");
        }
    }

    private ProductVariantAdminResponse mapAdmin(ProductVariant v) {
        return ProductVariantAdminResponse.builder().id(v.getId()).productId(v.getProduct().getId())
                .sku(v.getSku()).supplierSku(v.getSupplierSku()).supplierCost(v.getSupplierCost())
                .name(v.getOptionName()).value(v.getOptionValue()).price(v.getPrice())
                .currency(v.getProduct().getCurrency()).availableQuantity(v.getAvailableQuantity()).imageUrl(v.getImageUrl())
                .active(v.getActive()).createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt()).build();
    }

    private ProductVariantResponse map(ProductVariant v) {
        return ProductVariantResponse.builder().id(v.getId()).productId(v.getProduct().getId())
                .sku(v.getSku()).name(v.getOptionName()).value(v.getOptionValue()).price(v.getPrice())
                .currency(v.getProduct().getCurrency()).availableQuantity(v.getAvailableQuantity()).imageUrl(v.getImageUrl())
                .active(v.getActive()).createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt()).build();
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}