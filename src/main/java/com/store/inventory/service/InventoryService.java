package com.store.inventory.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.UpdateStockRequest;
import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.PublicAvailability;
import com.store.inventory.repository.InventoryRepository;
import com.store.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public Inventory initializeForProduct(Product product) {
        return inventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder()
                                .product(product)
                                .quantity(0)
                                .reservedQuantity(0)
                                .lowStockThreshold(5)
                                .build()));
    }

    public InventoryResponse getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
        return mapToResponse(inventory);
    }

    public int getAvailableQuantity(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> inv.getQuantity() - inv.getReservedQuantity())
                .orElse(0);
    }

    public PublicAvailability getPublicAvailability(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> (inv.getQuantity() - inv.getReservedQuantity()) > 0
                        ? PublicAvailability.IN_STOCK
                        : PublicAvailability.OUT_OF_STOCK)
                .orElse(PublicAvailability.UNKNOWN);
    }

    @Transactional
    public InventoryResponse updateStock(Long productId, UpdateStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));

        inventory.setQuantity(request.getQuantity());
        if (request.getLowStockThreshold() != null) {
            inventory.setLowStockThreshold(request.getLowStockThreshold());
        }

        Inventory saved = inventoryRepository.save(inventory);
        return mapToResponse(saved);
    }

    @Transactional
    public void deductStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));

        int available = inventory.getQuantity() - inventory.getReservedQuantity();
        if (available < quantity) {
            throw new BusinessException("Insufficient stock for product id: " + productId);
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        int available = inventory.getQuantity() - inventory.getReservedQuantity();
        boolean isLow = available <= inventory.getLowStockThreshold();

        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProduct().getId())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(available)
                .lowStockThreshold(inventory.getLowStockThreshold())
                .isLowStock(isLow)
                .availability(available > 0 ? PublicAvailability.IN_STOCK : PublicAvailability.OUT_OF_STOCK)
                .build();
    }
}
