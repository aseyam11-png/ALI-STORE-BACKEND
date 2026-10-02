package com.store.inventory.service;

import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class SupplierInventorySyncService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final SupplierInventoryClient supplierInventoryClient;

    @Value("${app.inventory-sync.markup-percentage:30}")
    private BigDecimal markupPercentage;

    @Transactional
    public void syncAllActiveSuppliers() {
        supplierRepository.findByStatus(SupplierStatus.ACTIVE).stream()
                .filter(supplier -> Boolean.TRUE.equals(supplier.getInventorySyncEnabled()))
                .forEach(this::syncSupplier);
    }

    @Transactional
    public void syncSupplier(Supplier supplier) {
        for (Product product : productRepository.findBySupplierIdAndSupplierSkuIsNotNull(supplier.getId())) {
            SupplierInventoryClient.SupplierInventoryResult result = supplierInventoryClient.fetch(supplier, product);

            inventoryService.syncSupplierStock(
                    product.getId(),
                    result.quantity(),
                    result.syncedAt());

            product.setSupplierCost(result.supplierCost());

            BigDecimal sellingPrice = result.supplierCost()
                    .multiply(BigDecimal.ONE.add(markupPercentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

            product.setPrice(sellingPrice);
            product.setCurrency(result.currency() == null || result.currency().isBlank()
                    ? product.getCurrency()
                    : result.currency().toUpperCase());

            productRepository.save(product);
        }
    }
}
