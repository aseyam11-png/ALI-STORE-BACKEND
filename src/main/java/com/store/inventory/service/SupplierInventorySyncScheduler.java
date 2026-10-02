package com.store.inventory.service;

import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.pricing.service.PricingRuleService;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierInventorySyncScheduler {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final PricingRuleService pricingRuleService;
    private final Environment environment;

    @Value("${app.fulfillment.inventory-sync-enabled:false}")
    private boolean enabled;

    @Scheduled(
            fixedDelayString = "${app.fulfillment.inventory-poll-ms:60000}",
            initialDelayString = "${app.fulfillment.inventory-initial-delay-ms:15000}"
    )
    public void synchronizeSupplierInventory() {
        if (!enabled) return;

        String token = environment.getProperty("app.fulfillment.api-token", "");
        String apiPath = environment.getProperty("app.fulfillment.inventory-api-path", "/v1/inventory");
        if (token == null || token.isBlank()) {
            log.warn("Supplier inventory polling skipped: SUPPLIER_API_TOKEN is not configured");
            return;
        }

        for (Supplier supplier : supplierRepository.findByStatus(SupplierStatus.ACTIVE)) {
            if (!Boolean.TRUE.equals(supplier.getInventorySyncEnabled())
                    || supplier.getApiBaseUrl() == null
                    || supplier.getApiBaseUrl().isBlank()) {
                continue;
            }
            synchronizeSupplier(supplier, token, apiPath);
        }
    }

    private void synchronizeSupplier(Supplier supplier, String token, String apiPath) {
        String endpoint = supplier.getApiBaseUrl().replaceAll("/+$", "") + apiPath;
        try {
            SupplierInventoryBatchResponse response = RestClient.builder()
                    .build()
                    .get()
                    .uri(endpoint)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Supplier-Code", supplier.getExternalCode())
                    .retrieve()
                    .body(SupplierInventoryBatchResponse.class);

            if (response == null || response.items() == null) return;

            for (SupplierInventoryItem item : response.items()) {
                if (item == null || item.supplierSku() == null || item.supplierSku().isBlank()) continue;

                Product product = productRepository.findBySupplierSku(item.supplierSku().trim()).orElse(null);
                if (product == null || product.getSupplier() == null
                        || !supplier.getId().equals(product.getSupplier().getId())) continue;

                if (item.supplierPrice() != null && item.supplierPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    product.setSupplierCost(item.supplierPrice());
                    productRepository.save(product);
                    pricingRuleService.recalculateProduct(product.getId());
                }

                inventoryService.syncSupplierStock(
                        product.getId(),
                        Math.max(0, item.quantity()),
                        item.syncedAt() != null ? item.syncedAt() : LocalDateTime.now()
                );
            }
        } catch (RuntimeException ex) {
            log.error("Supplier inventory sync failed for {}", supplier.getExternalCode(), ex);
        }
    }

    public record SupplierInventoryItem(
            String supplierSku,
            int quantity,
            java.math.BigDecimal supplierPrice,
            LocalDateTime syncedAt
    ) {}

    public record SupplierInventoryBatchResponse(
            List<SupplierInventoryItem> items
    ) {}
}
