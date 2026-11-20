package com.store.inventory.service;

import com.store.inventory.dto.SupplierInventoryResponse;
import com.store.supplier.entity.Supplier;
import com.store.supplier.service.SupplierCredentialResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class HttpSupplierInventoryClient implements SupplierInventoryClient {

    private final Environment environment;
    private final SupplierCredentialResolver credentialResolver;

    @Override
    public SupplierInventoryResponse fetchInventory(Supplier supplier) {
        if (!Boolean.TRUE.equals(supplier.getInventorySyncEnabled())) {
            throw new IllegalStateException("Supplier inventory sync is disabled");
        }
        if (supplier.getApiBaseUrl() == null || supplier.getApiBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "Supplier inventory API is not configured for supplier "
                            + supplier.getExternalCode());
        }

        String apiPath = environment.getProperty(
                "app.inventory-sync.api-path", "/v1/inventory");
        String apiToken = credentialResolver.resolveApiToken(supplier);

        if (apiToken.isBlank()) {
            throw new IllegalStateException(
                    "No supplier API token is configured for "
                            + supplier.getExternalCode());
        }

        String endpoint = supplier.getApiBaseUrl().replaceAll("/+$", "") + apiPath;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
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
                throw new IllegalStateException(
                        "Supplier returned an invalid inventory response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "Supplier inventory request failed", ex);
        }
    }
}
