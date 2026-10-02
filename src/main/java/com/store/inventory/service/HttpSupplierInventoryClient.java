package com.store.inventory.service;

import com.store.product.entity.Product;
import com.store.supplier.entity.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class HttpSupplierInventoryClient implements SupplierInventoryClient {

    private final String apiPath;
    private final String apiToken;

    public HttpSupplierInventoryClient(
            @Value("${app.inventory-sync.api-path:/v1/inventory/{supplierSku}}") String apiPath,
            @Value("${app.inventory-sync.api-token:}") String apiToken) {
        this.apiPath = apiPath;
        this.apiToken = apiToken;
    }

    @Override
    public SupplierInventoryResult fetch(Supplier supplier, Product product) {
        if (!Boolean.TRUE.equals(supplier.getInventorySyncEnabled())) {
            throw new IllegalStateException("Supplier inventory sync is disabled");
        }
        if (supplier.getApiBaseUrl() == null || supplier.getApiBaseUrl().isBlank()) {
            throw new IllegalStateException("Supplier inventory API is not configured");
        }
        if (apiToken.isBlank()) {
            throw new IllegalStateException("SUPPLIER_API_TOKEN is not configured");
        }
        if (product.getSupplierSku() == null || product.getSupplierSku().isBlank()) {
            throw new IllegalStateException("Supplier SKU is required");
        }

        String path = apiPath.replace("{supplierSku}", product.getSupplierSku());
        String endpoint = supplier.getApiBaseUrl().replaceAll("/+$", "") + path;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);

        try {
            ExternalInventoryResponse response = RestClient.builder()
                    .requestFactory(factory)
                    .build()
                    .get()
                    .uri(endpoint)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiToken)
                    .retrieve()
                    .body(ExternalInventoryResponse.class);

            if (response == null || response.stock() < 0 ||
                    response.supplierCost() == null || response.supplierCost().signum() <= 0) {
                throw new IllegalStateException("Supplier returned an invalid inventory response");
            }

            return new SupplierInventoryResult(
                    response.stock(),
                    response.supplierCost(),
                    response.currency(),
                    response.syncedAt() != null ? response.syncedAt() : LocalDateTime.now());
        } catch (RestClientException ex) {
            throw new IllegalStateException("Supplier inventory request failed", ex);
        }
    }

    private record ExternalInventoryResponse(
            int stock,
            BigDecimal supplierCost,
            String currency,
            LocalDateTime syncedAt
    ) {}
}
