package com.store.fulfillment.dto;

import com.store.fulfillment.entity.FulfillmentTaskStatus;
import java.time.LocalDateTime;

public record FulfillmentTaskAttentionResponse(
        Long taskId,
        FulfillmentTaskStatus status,
        Integer attempts,
        Long orderId,
        Long shipmentId,
        String supplierCode,
        String lastError,
        LocalDateTime updatedAt) {
}
