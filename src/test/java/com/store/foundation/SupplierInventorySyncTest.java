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
        return payload(null, supplierSku, quantity, cost, observedAt);
    }

    private SupplierInventoryResponse payload(String supplierProductId, String supplierSku, int quantity, BigDecimal cost, LocalDateTime observedAt) {
        SupplierInventoryResponse.Item item = new SupplierInventoryResponse.Item();
        item.setSupplierProductId(supplierProductId);
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
    void sync_prefersSupplierProductIdWhenSupplierSkuChanges() {
        Product product = createProduct("SYNC-004", new BigDecimal("25.00"));
        product.setSupplierProductId("EXT-PROD-004");
        productRepository.save(product);
        LocalDateTime observedAt = LocalDateTime.now().plusMinutes(1);
        when(supplierInventoryClient.fetchInventory(supplier))
                .thenReturn(payload("EXT-PROD-004", "SUP-SYNC-004-RENAMED", 13, new BigDecimal("12.00"), observedAt));

        var report = syncService.syncSupplier(supplier.getId());

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertEquals(1, report.getMatchedProducts());
        assertEquals(13, inventory.getQuantity());
        assertEquals("EXT-PROD-004", productRepository.findById(product.getId()).orElseThrow().getSupplierProductId());
    }

    @Test
    void sync_adoptsSupplierProductIdForExistingSkuMapping() {
        Product product = createProduct("SYNC-005", new BigDecimal("25.00"));
        LocalDateTime observedAt = LocalDateTime.now().plusMinutes(1);
        when(supplierInventoryClient.fetchInventory(supplier))
                .thenReturn(payload("EXT-PROD-005", "SUP-SYNC-005", 7, new BigDecimal("12.00"), observedAt));

        syncService.syncSupplier(supplier.getId());

        assertEquals("EXT-PROD-005", productRepository.findById(product.getId()).orElseThrow().getSupplierProductId());
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


    @Test
    void sync_rejectsSupplierCostWhenCurrencyIsMissingWithoutPartialUpdate() {
        Product product = createProduct("SYNC-007", new BigDecimal("25.00"));
        LocalDateTime previousSyncAt = inventoryRepository.findByProductId(product.getId()).orElseThrow().getLastSyncedAt();
        LocalDateTime observedAt = LocalDateTime.now().plusMinutes(1);
        SupplierInventoryResponse payload = payload(
                "SUP-SYNC-007", 9, new BigDecimal("12.50"), observedAt);
        payload.getItems().get(0).setCurrency(null);
        when(supplierInventoryClient.fetchInventory(supplier)).thenReturn(payload);

        var report = syncService.syncSupplier(supplier.getId());

        Product unchangedProduct = productRepository.findById(product.getId()).orElseThrow();
        Inventory unchangedInventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertEquals(new BigDecimal("10.00"), unchangedProduct.getSupplierCost());
        assertEquals(2, unchangedInventory.getQuantity(),
                "The SKU transaction must roll back stock when its cost payload is invalid");
        assertEquals(previousSyncAt, unchangedInventory.getLastSyncedAt());
        assertEquals(1, report.getRejectedItems());
    }

    @Test
    void sync_rejectsSupplierCostWhenCurrencyDoesNotMatchProduct() {
        Product product = createProduct("SYNC-008", new BigDecimal("25.00"));
        LocalDateTime observedAt = LocalDateTime.now().plusMinutes(1);
        SupplierInventoryResponse payload = payload(
                "SUP-SYNC-008", 9, new BigDecimal("12.50"), observedAt);
        payload.getItems().get(0).setCurrency("EUR");
        when(supplierInventoryClient.fetchInventory(supplier)).thenReturn(payload);

        var report = syncService.syncSupplier(supplier.getId());

        Product unchangedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(new BigDecimal("10.00"), unchangedProduct.getSupplierCost());
        assertEquals(1, report.getRejectedItems());
    }

}
