package com.store.fulfillment.service;

import com.store.fulfillment.dto.SupplierFulfillmentRequest;
import com.store.fulfillment.dto.SupplierFulfillmentResponse;
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
public class HttpSupplierFulfillmentClient implements SupplierFulfillmentClient {

    private final Environment environment;
    private final SupplierCredentialResolver credentialResolver;

    @Override
    public SupplierFulfillmentResponse submit(Supplier supplier, SupplierFulfillmentRequest request) {
        if (!Boolean.TRUE.equals(supplier.getFulfillmentEnabled())) {
            throw new IllegalStateException("Supplier fulfillment is disabled");
        }
        if (supplier.getApiBaseUrl() == null || supplier.getApiBaseUrl().isBlank()) {
            throw new IllegalStateException("Supplier fulfillment API is not configured for supplier " + supplier.getExternalCode());
        }

        String apiPath = environment.getProperty("app.fulfillment.api-path", "/v1/orders");
        String apiToken = credentialResolver.resolveApiToken(supplier);
        if (apiToken.isBlank()) {
            throw new IllegalStateException("No supplier API token is configured for " + supplier.getExternalCode());
        }

        String endpoint = supplier.getApiBaseUrl().replaceAll("/+$", "") + apiPath;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);

        try {
            SupplierFulfillmentResponse response = RestClient.builder()
                    .requestFactory(factory)
                    .build()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Idempotency-Key", request.getIdempotencyKey())
                    .body(request)
                    .retrieve()
                    .body(SupplierFulfillmentResponse.class);

            if (response == null || !Boolean.TRUE.equals(response.getAccepted())
                    || response.getSupplierOrderId() == null || response.getSupplierOrderId().isBlank()) {
                throw new IllegalStateException("Supplier rejected or returned an invalid fulfillment response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Supplier fulfillment request failed", ex);
        }
    }
}
