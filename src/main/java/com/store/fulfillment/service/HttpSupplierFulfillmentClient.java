package com.store.fulfillment.service;

import com.store.fulfillment.dto.SupplierFulfillmentRequest;
import com.store.fulfillment.dto.SupplierFulfillmentResponse;
import com.store.supplier.entity.Supplier;
import com.store.supplier.service.SupplierCredentialResolver;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpSupplierFulfillmentClient implements SupplierFulfillmentClient {

    private final Environment environment;
    private final SupplierCredentialResolver credentialResolver;

    public HttpSupplierFulfillmentClient(
            Environment environment,
            SupplierCredentialResolver credentialResolver) {
        this.environment = environment;
        this.credentialResolver = credentialResolver;
    }

    @Override
    public SupplierFulfillmentResponse submit(Supplier supplier, SupplierFulfillmentRequest request) {
        if (supplier == null
                || supplier.getStatus() != com.store.supplier.entity.SupplierStatus.ACTIVE) {
            throw new IllegalStateException("Supplier is not active for fulfillment");
        }
        if (!Boolean.TRUE.equals(supplier.getFulfillmentEnabled())) {
            throw new IllegalStateException("Supplier fulfillment is disabled");
        }
        if (supplier.getApiBaseUrl() == null || supplier.getApiBaseUrl().isBlank()) {
            throw new IllegalStateException("Supplier fulfillment API is not configured for supplier " + supplier.getExternalCode());
        }

        String apiPath = environment.getProperty("app.fulfillment.api-path", "/v1/orders");
        String apiToken = credentialResolver.resolveFulfillmentToken(supplier);
        if (apiToken.isBlank()) {
            throw new IllegalStateException(
                    "No fulfillment API token is configured for supplier "
                            + supplier.getExternalCode());
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

            if (response == null) {
                throw new SupplierOutcomeUnknownException(
                        "Supplier returned no response; external order outcome cannot be determined");
            }
            if (Boolean.FALSE.equals(response.getAccepted())) {
                throw new IllegalStateException("Supplier explicitly rejected the fulfillment request");
            }
            if (!Boolean.TRUE.equals(response.getAccepted())) {
                throw new SupplierOutcomeUnknownException(
                        "Supplier response did not provide a definitive acceptance decision");
            }
            if (response.getSupplierOrderId() == null || response.getSupplierOrderId().isBlank()) {
                throw new SupplierOutcomeUnknownException(
                        "Supplier accepted or may have accepted the order but returned no usable supplier order ID");
            }

            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new IllegalStateException(
                        "Supplier rejected fulfillment request with HTTP " + ex.getStatusCode().value(), ex);
            }
            throw new SupplierOutcomeUnknownException(
                    "Supplier returned HTTP " + ex.getStatusCode().value()
                            + "; acceptance outcome is unknown", ex);
        } catch (RestClientException ex) {
            throw new SupplierOutcomeUnknownException(
                    "Supplier transport failed; acceptance outcome is unknown", ex);
        }
    }
}
