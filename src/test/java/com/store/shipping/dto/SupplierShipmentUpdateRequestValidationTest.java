package com.store.shipping.dto;

import com.store.shipping.entity.ShipmentStatus;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierShipmentUpdateRequestValidationTest {

    @Test
    void rejectsTrackingNumbersLongerThanTheShipmentColumnLimit() {
        var request = validRequest();
        request.setTrackingNumber("T".repeat(101));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);
            assertTrue(violations.stream().anyMatch(violation ->
                    violation.getPropertyPath().toString().equals("trackingNumber")));
        }
    }

    @Test
    void rejectsCarrierNamesLongerThanTheShipmentColumnLimit() {
        var request = validRequest();
        request.setCarrier("C".repeat(101));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);
            assertTrue(violations.stream().anyMatch(violation ->
                    violation.getPropertyPath().toString().equals("carrier")));
        }
    }

    @Test
    void rejectsSupplierOrderIdentifiersLongerThanTheShipmentColumnLimit() {
        var request = validRequest();
        request.setSupplierOrderId("S".repeat(256));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);
            assertTrue(violations.stream().anyMatch(violation ->
                    violation.getPropertyPath().toString().equals("supplierOrderId")));
        }
    }

    private SupplierShipmentUpdateRequest validRequest() {
        var request = new SupplierShipmentUpdateRequest();
        request.setSupplierCode("alpha");
        request.setSupplierOrderId("SUP-1");
        request.setStatus(ShipmentStatus.IN_TRANSIT);
        return request;
    }
}
