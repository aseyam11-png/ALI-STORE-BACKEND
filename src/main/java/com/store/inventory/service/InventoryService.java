package com.store.inventory.service;

import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.UpdateStockRequest;
import com.store.inventory.entity.Inventory;
import com.store.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryResponse getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product id: " + productId));
        return mapToResponse(inventory);
    }

    @Transactional
    public InventoryResponse updateStock(Long productId, UpdateStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> Inventory.builder().productId(productId).reservedQuantity(0).build());

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
                .orElseThrow(() -> new RuntimeException("Inventory not found for product id: " + productId));

        if (inventory.getQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock for product id: " + productId);
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        int available = inventory.getQuantity() - inventory.getReservedQuantity();
        boolean isLow = available <= inventory.getLowStockThreshold();

        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(available)
                .lowStockThreshold(inventory.getLowStockThreshold())
                .isLowStock(isLow)
                .build();
    }
}