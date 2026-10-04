package com.store.foundation;

import com.store.inventory.dto.SupplierInventoryResponse;
import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.InventorySource;
import com.store.inventory.repository.InventoryRepository;
import com.store.inventory.service.SupplierInventoryClient;
import com.store.inventory.service.InventoryService;
import com.store.inventory.service.SupplierInventorySyncService;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SupplierInventorySyncTest {

    @Autowired private SupplierInventorySyncService syncService;
    @Autowired private InventoryService inventoryService;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;

    @MockBean private SupplierInventoryClient supplierInventoryClient;

    private Supplier supplier;
    private Category category;

    @BeforeEach
    void setUp() {
        supplier = supplierRepository.save(Supplier.builder()
                .externalCode("SYNC-SUPPLIER")
                .name("Sync Supplier")
                .status(com.store.supplier.entity.SupplierStatus.ACTIVE)
                .fulfillmentEnabled(true)
                .inventorySyncEnabled(true)
                .apiBaseUrl("https://supplier.example")
                .build());

        category = categoryRepository.save(Category.builder()
                .name("Sync Test")
                .slug("sync-test")
                .active(true)
                .build());
    }

    private Product createProduct(String sku, BigDecimal price) {
        Product product = productRepository.save(Product.builder()
                .name("Sync " + sku)
                .slug("sync-" + sku.toLowerCase())
                .price(price)
                .currency("USD")
                .sku(sku)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .supplier(supplier)
                .supplierSku("SUP-" + sku)
                .supplierCost(new BigDecimal("10.00"))
                .build());

        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(2)
                .reservedQuantity(0)
                .lowStockThreshold(1)
                .source(InventorySource.MANUAL)
                .lastSyncedAt(LocalDateTime.now().minusHours(1))
                .build());
        return product;
    }

    private SupplierInventoryResponse payload(String supplierSku, int quantity, BigDecimal cost, LocalDateTime observedAt) {
        SupplierInventoryResponse.Item item = new SupplierInventoryResponse.Item();
        item.setSupplierSku(supplierSku);
        item.setQuantity(quantity);
        item.setUnitCost(cost);
        item.setCurrency("USD");
        item.setObservedAt(observedAt);

        SupplierInventoryResponse response = new SupplierInventoryResponse();
        response.setItems(List.of(item));
        return response;
    }

    @Test
    void sync_updatesSupplierStockAndSourceCost() {
        Product product = createProduct("SYNC-001", new BigDecimal("25.00"));
        LocalDateTime observedAt = LocalDateTime.now().plusMinutes(1);
        when(supplierInventoryClient.fetchInventory(supplier))
                .thenReturn(payload("SUP-SYNC-001", 11, new BigDecimal("12.50"), observedAt));

        var report = syncService.syncSupplier(supplier.getId());

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();

        assertEquals(1, report.getMatchedProducts());
        assertEquals(1, report.getUpdatedProducts());
        assertEquals(new BigDecimal("12.50"), updatedProduct.getSupplierCost());
        assertEquals(11, inventory.getQuantity());
        assertEquals(InventorySource.SUPPLIER_SYNC, inventory.getSource());
        assertEquals(observedAt, inventory.getLastSyncedAt());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void sync_neverLeavesActiveProductBelowCurrentSupplierCost() {
        Product product = createProduct("SYNC-002", new BigDecimal("25.00"));
        LocalDateTime observedAt = LocalDateTime.now().plusMinutes(1);
        when(supplierInventoryClient.fetchInventory(supplier))
                .thenReturn(payload("SUP-SYNC-002", 9, new BigDecimal("30.00"), observedAt));

        syncService.syncSupplier(supplier.getId());

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(new BigDecimal("30.00"), updatedProduct.getSupplierCost());
        assertEquals(ProductStatus.DRAFT, updatedProduct.getStatus());
        assertNotEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }
    @Test
    void staleSupplierSnapshotMakesAvailabilityUnknown() {
        Product product = createProduct("SYNC-003", new BigDecimal("25.00"));

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        inventory.setSource(InventorySource.SUPPLIER_SYNC);
        inventory.setLastSyncedAt(LocalDateTime.now().minusHours(2));
        inventoryRepository.save(inventory);

        assertEquals(com.store.inventory.enums.PublicAvailability.UNKNOWN,
                inventoryService.getPublicAvailability(product.getId()));
        assertEquals(0, inventoryService.getAvailableQuantity(product.getId()));
    }


}
