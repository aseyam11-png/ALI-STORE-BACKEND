package com.store.fulfillment.controller;

import com.store.common.response.ApiResponse;
import com.store.fulfillment.dto.FulfillmentManualRetryRequest;
import com.store.fulfillment.dto.FulfillmentTaskAttentionResponse;
import com.store.fulfillment.service.FulfillmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/fulfillment/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FulfillmentOperationsController {

    private final FulfillmentService fulfillmentService;

    @GetMapping("/attention")
    public ResponseEntity<ApiResponse<List<FulfillmentTaskAttentionResponse>>> attention() {
        return ResponseEntity.ok(ApiResponse.success(
                fulfillmentService.getTasksNeedingAttention(),
                "Fulfillment tasks requiring attention retrieved"));
    }

    @PostMapping("/{taskId}/requeue")
    public ResponseEntity<ApiResponse<Void>> requeue(
            @PathVariable Long taskId,
            @Valid @RequestBody FulfillmentManualRetryRequest request) {
        fulfillmentService.requeueAfterManualConfirmation(
                taskId, request.confirmation(), request.operatorNote());
        return ResponseEntity.ok(ApiResponse.success(null, "Fulfillment task requeued after manual confirmation"));
    }
}
