package com.store.supplier.service;

import com.store.common.exception.BusinessException;
import com.store.inventory.entity.Inventory;
import com.store.inventory.repository.InventoryRepository;
import com.store.pricing.service.PricingRuleService;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SupplierInventorySyncService {
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PricingRuleService pricingRuleService;
    private final SupplierInventoryClient supplierInventoryClient;

    @Transactional
    public void syncAll() {
        productRepository.findAll().stream()
                .filter(p -> p.getSupplier() != null)
                .filter(p -> Boolean.TRUE.equals(p.getSupplier().getInventorySyncEnabled()))
                .filter(p -> p.getSupplierSku() != null && !p.getSupplierSku().isBlank())
                .forEach(this::syncProductSafely);
    }

    private void syncProductSafely(Product product) {
        try {
            syncProduct(product);
        } catch (RuntimeException ex) {
            // One supplier/API failure must not abort synchronization for every other product.
            // The worker will retry on the next scheduled run.
        }
    }

    @Transactional
    public void syncProduct(Product product) {
        Supplier supplier = product.getSupplier();
        if (supplier == null || product.getSupplierSku() == null || product.getSupplierSku().isBlank()) {
            throw new BusinessException("Product has no supplier inventory mapping");
        }

        SupplierInventoryClient.InventorySnapshot snapshot =
                supplierInventoryClient.fetch(supplier, product.getSupplierSku());

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(product.getId())
                .orElseThrow(() -> new BusinessException("Inventory not initialized for product " + product.getId()));

        if (snapshot.quantity() < inventory.getReservedQuantity()) {
            throw new BusinessException("Supplier stock is below currently reserved quantity for product " + product.getId());
        }

        inventory.setQuantity(snapshot.quantity());
        inventory.setSource(com.store.inventory.enums.InventorySource.SUPPLIER_SYNC);
        inventory.setLastSyncedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        product.setSupplierCost(snapshot.unitCost());
        product.setCurrency(snapshot.currency());
        product.setPrice(pricingRuleService.calculateSellingPrice(product));
        productRepository.save(product);
    }
}