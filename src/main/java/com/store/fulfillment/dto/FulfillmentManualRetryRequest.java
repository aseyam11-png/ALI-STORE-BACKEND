package com.store.fulfillment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FulfillmentManualRetryRequest(
        @NotBlank String confirmation,
        @Size(max = 500) String operatorNote) {
}
