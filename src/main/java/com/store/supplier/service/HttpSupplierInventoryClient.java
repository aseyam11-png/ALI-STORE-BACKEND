package com.store.supplier.service;

import com.store.supplier.entity.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Component
public class HttpSupplierInventoryClient implements SupplierInventoryClient {
    private final Environment environment;

    public HttpSupplierInventoryClient(Environment environment) {
        this.environment = environment;
    }

    @Override
    public InventorySnapshot fetch(Supplier supplier, String supplierSku) {
        if (!Boolean.TRUE.equals(supplier.getInventorySyncEnabled())) {
            throw new IllegalStateException("Supplier inventory sync is disabled");
        }
        if (supplier.getApiBaseUrl() == null || supplier.getApiBaseUrl().isBlank()) {
            throw new IllegalStateException("Supplier inventory API is not configured for supplier " + supplier.getExternalCode());
        }

        String path = environment.getProperty("app.supplier-sync.inventory-path", "/v1/inventory");
        String token = environment.getProperty("app.supplier-sync.api-token", environment.getProperty("SUPPLIER_API_TOKEN", ""));
        if (token.isBlank()) {
            throw new IllegalStateException("SUPPLIER_API_TOKEN is not configured");
        }

        String base = supplier.getApiBaseUrl().replaceAll("/+$", "");
        String endpoint = base + path + (path.contains("?") ? "&" : "?")
                + "sku=" + java.net.URLEncoder.encode(supplierSku, java.nio.charset.StandardCharsets.UTF_8);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);

        try {
            InventoryBody body = RestClient.builder()
                    .requestFactory(factory)
                    .build()
                    .get()
                    .uri(endpoint)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(InventoryBody.class);

            if (body == null || body.quantity == null || body.unitCost == null || body.quantity < 0 || body.unitCost.signum() <= 0) {
                throw new IllegalStateException("Supplier returned an invalid inventory payload");
            }
            return new InventorySnapshot(body.quantity, body.unitCost, body.currency == null || body.currency.isBlank() ? "SAR" : body.currency);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Supplier inventory request failed", ex);
        }
    }

    private record InventoryBody(Integer quantity, BigDecimal unitCost, String currency) {}
}