package com.store.supplier.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.product.dto.ProductResponse;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.inventory.service.InventoryService;
import com.store.supplier.dto.SupplierProductPriceUpdateRequest;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class SupplierProductPriceSyncService {
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;

    @Value("${app.pricing.default-markup-percentage:30}")
    private BigDecimal defaultMarkupPercentage;

    @Transactional
    public ProductResponse synchronize(SupplierProductPriceUpdateRequest request) {
        Supplier supplier = supplierRepository.findByExternalCode(request.getSupplierCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));

        if (!Boolean.TRUE.equals(supplier.getInventorySyncEnabled())
                || supplier.getStatus() != com.store.supplier.entity.SupplierStatus.ACTIVE) {
            throw new BusinessException("Supplier is not active for catalog synchronization");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getSupplier() == null || !product.getSupplier().getId().equals(supplier.getId())) {
            throw new ForbiddenException("Supplier is not authorized for this product");
        }

        if (product.getSupplierSku() == null
                || !product.getSupplierSku().equals(request.getSupplierSku().trim())) {
            throw new ForbiddenException("Supplier SKU does not match the product mapping");
        }

        BigDecimal supplierCost = request.getSupplierCost().setScale(2, RoundingMode.HALF_UP);
        BigDecimal markup = defaultMarkupPercentage.max(BigDecimal.ZERO);
        BigDecimal sellingPrice = supplierCost
                .multiply(BigDecimal.ONE.add(markup.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        if (sellingPrice.compareTo(supplierCost) < 0) {
            throw new BusinessException("Calculated selling price cannot be lower than supplier cost");
        }

        product.setSupplierCost(supplierCost);
        product.setPrice(sellingPrice);
        productRepository.save(product);

        return toPublicResponse(product);
    }

    private ProductResponse toPublicResponse(Product product) {
        var imageUrls = product.getImages().stream()
                .sorted(java.util.Comparator.comparing(com.store.product.entity.ProductImage::getSortOrder))
                .map(com.store.product.entity.ProductImage::getUrl)
                .toList();

        String primaryImageUrl = product.getImages().stream()
                .filter(com.store.product.entity.ProductImage::getIsPrimary)
                .map(com.store.product.entity.ProductImage::getUrl)
                .findFirst()
                .orElse(imageUrls.isEmpty() ? null : imageUrls.get(0));

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .sku(product.getSku())
                .status(product.getStatus())
                .availability(inventoryService.getPublicAvailability(product.getId()))
                .availableQuantity(inventoryService.getAvailableQuantity(product.getId()))
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .images(imageUrls)
                .primaryImageUrl(primaryImageUrl)
                .build();
    }
}
