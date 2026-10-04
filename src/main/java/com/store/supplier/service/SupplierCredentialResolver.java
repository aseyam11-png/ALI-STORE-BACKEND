package com.store.supplier.service;

import com.store.supplier.entity.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SupplierCredentialResolver {

    private final Environment environment;

    public String resolveApiToken(Supplier supplier) {
        String code = normalize(supplier.getExternalCode());
        String token = environment.getProperty(
                "SUPPLIER_API_TOKEN_" + code, "");
        if (token.isBlank() && !isProd()) {
            token = environment.getProperty(
                    "app.inventory-sync.api-token", "");
        }
        return token == null ? "" : token.trim();
    }

    public String resolveFulfillmentToken(Supplier supplier) {
        String code = normalize(supplier.getExternalCode());
        String token = environment.getProperty(
                "app.fulfillment.api-tokens." + supplier.getExternalCode());
        if (token == null || token.isBlank()) {
            token = environment.getProperty(
                    "SUPPLIER_FULFILLMENT_TOKEN_" + code);
        }
        if ((token == null || token.isBlank()) && !isProd()) {
            token = environment.getProperty(
                    "app.fulfillment.api-token", "");
        }
        return token == null ? "" : token.trim();
    }

    public String resolveWebhookSecret(String supplierCode) {
        String code = normalize(supplierCode);
        String secret = environment.getProperty(
                "app.fulfillment.webhook-tokens." + supplierCode);
        if (secret == null || secret.isBlank()) {
            secret = environment.getProperty(
                    "SUPPLIER_WEBHOOK_TOKEN_" + code);
        }
        if ((secret == null || secret.isBlank()) && !isProd()) {
            secret = environment.getProperty(
                    "app.fulfillment.webhook-token", "");
        }
        return secret == null ? "" : secret.trim();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT)
                        .replaceAll("[^A-Z0-9]", "_");
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) return true;
        }
        return false;
    }
}
