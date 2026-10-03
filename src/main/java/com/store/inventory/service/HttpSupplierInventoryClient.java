package com.store.inventory.service;

import com.store.inventory.dto.SupplierInventoryResponse;
import com.store.supplier.entity.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpSupplierInventoryClient implements SupplierInventoryClient {

    private final String apiPath;
    private final String apiToken;

    public HttpSupplierInventoryClient(
            @Value("${app.inventory-sync.api-path:/v1/inventory}") String apiPath,
            @Value("${app.inventory-sync.api-token:}") String apiToken) {
        this.apiPath = apiPath;
        this.apiToken = apiToken;
    }

    @Override
    public SupplierInventoryResponse fetchInventory(Supplier supplier) {
        if (!Boolean.TRUE.equals(supplier.getInventorySyncEnabled())) {
            throw new IllegalStateException("Supplier inventory sync is disabled");
        }
        if (supplier.getApiBaseUrl() == null || supplier.getApiBaseUrl().isBlank()) {
            throw new IllegalStateException("Supplier inventory API is not configured for supplier "
                    + supplier.getExternalCode());
        }
        if (apiToken.isBlank()) {
            throw new IllegalStateException("SUPPLIER_API_TOKEN is not configured");
        }

        String endpoint = supplier.getApiBaseUrl().replaceAll("/+$", "") + apiPath;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        try {
            SupplierInventoryResponse response = RestClient.builder()
                    .requestFactory(factory)
                    .build()
                    .get()
                    .uri(endpoint)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiToken)
                    .retrieve()
                    .body(SupplierInventoryResponse.class);

            if (response == null || response.getItems() == null) {
                throw new IllegalStateException("Supplier returned an empty inventory payload");
            }

            return response;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Supplier inventory request failed", ex);
        }
    }
}
