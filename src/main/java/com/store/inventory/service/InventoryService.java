package com.store.inventory.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.UpdateStockRequest;
import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.InventorySource;
import com.store.inventory.enums.PublicAvailability;
import com.store.inventory.repository.InventoryRepository;
import com.store.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventoryService {

    @Value("${app.inventory-sync.stale-after-ms:1800000}")
    private long staleAfterMs;

    private final InventoryRepository inventoryRepository;

    @Transactional
    public Inventory initializeForProduct(Product product) {
        return inventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .product(product).quantity(0).reservedQuantity(0)
                        .lowStockThreshold(5).source(null).lastSyncedAt(null).build()));
    }

    public InventoryResponse getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        return mapToResponse(inventory);
    }

    public int getAvailableQuantity(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .filter(this::isKnown)
                .map(inv -> Math.max(0, inv.getQuantity() - inv.getReservedQuantity()))
                .orElse(0);
    }

    @Transactional
    public boolean isAvailableForQuantity(Long productId, int quantity) {
        if (quantity < 1) return false;
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        return isKnown(inventory) && inventory.getQuantity() - inventory.getReservedQuantity() >= quantity;
    }

    public PublicAvailability getPublicAvailability(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> {
                    if (!isKnown(inv)) return PublicAvailability.UNKNOWN;
                    return (inv.getQuantity() - inv.getReservedQuantity()) > 0
                            ? PublicAvailability.IN_STOCK : PublicAvailability.OUT_OF_STOCK;
                }).orElse(PublicAvailability.UNKNOWN);
    }

    @Transactional
    public void reserveStock(Long productId, int quantity) {
        if (quantity < 1) throw new BusinessException("Reservation quantity must be at least 1");
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        if (!isKnown(inventory)) throw new BusinessException("Inventory is not synchronized for product id: " + productId);
        int available = inventory.getQuantity() - inventory.getReservedQuantity();
        if (available < quantity) throw new BusinessException("Insufficient stock for product id: " + productId);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void commitReservedStock(Long productId, int quantity) {
        if (quantity < 1) return;
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        if (inventory.getReservedQuantity() < quantity || inventory.getQuantity() < quantity) {
            throw new BusinessException("Reserved stock cannot be committed for product id: " + productId);
        }
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void releaseReservedStock(Long productId, int quantity) {
        releaseReservedStockUpTo(productId, quantity);
    }

    @Transactional
    public void releaseReservedStockUpTo(Long productId, int quantity) {
        if (quantity < 1) return;
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        int release = Math.min(quantity, inventory.getReservedQuantity());
        inventory.setReservedQuantity(inventory.getReservedQuantity() - release);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public InventoryResponse syncSupplierStock(Long productId, int quantity, LocalDateTime syncedAt) {
        if (quantity < 0) throw new BusinessException("Supplier inventory quantity cannot be negative");
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        if (quantity < inventory.getReservedQuantity()) {
            throw new BusinessException("Supplier quantity cannot be lower than currently reserved stock");
        }
        inventory.setQuantity(quantity);
        inventory.setSource(InventorySource.SUPPLIER_SYNC);
        inventory.setLastSyncedAt(syncedAt != null ? syncedAt : LocalDateTime.now());
        return mapToResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    public InventoryResponse updateStock(Long productId, UpdateStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        if (request.getQuantity() < inventory.getReservedQuantity()) {
            throw new BusinessException("Quantity cannot be lower than currently reserved stock");
        }
        inventory.setQuantity(request.getQuantity());
        inventory.setSource(InventorySource.MANUAL);
        inventory.setLastSyncedAt(LocalDateTime.now());
        if (request.getLowStockThreshold() != null) inventory.setLowStockThreshold(request.getLowStockThreshold());
        return mapToResponse(inventoryRepository.save(inventory));
    }

    private boolean isKnown(Inventory inventory) {
        if (inventory.getSource() == InventorySource.MANUAL) return true;
        if (inventory.getLastSyncedAt() == null) return false;
        long age = java.time.Duration.between(inventory.getLastSyncedAt(), LocalDateTime.now()).toMillis();
        return age >= 0 && age <= staleAfterMs;
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        int available = Math.max(0, inventory.getQuantity() - inventory.getReservedQuantity());
        boolean known = isKnown(inventory);
        return InventoryResponse.builder()
                .id(inventory.getId()).productId(inventory.getProduct().getId())
                .quantity(inventory.getQuantity()).reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(available).lowStockThreshold(inventory.getLowStockThreshold())
                .isLowStock(known && available <= inventory.getLowStockThreshold())
                .availability(!known ? PublicAvailability.UNKNOWN : (available > 0 ? PublicAvailability.IN_STOCK : PublicAvailability.OUT_OF_STOCK))
                .source(inventory.getSource()).lastSyncedAt(inventory.getLastSyncedAt())
                .build();
    }
}
