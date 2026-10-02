package com.store.fulfillment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps durable fulfillment tasks moving without requiring a request thread.
 * Supplier calls remain outside the database transaction inside
 * FulfillmentTaskProcessor, with idempotency and retry handling there.
 */
@Component
@RequiredArgsConstructor
public class FulfillmentTaskScheduler {

    private final FulfillmentService fulfillmentService;

    @Scheduled(fixedDelayString = "${app.fulfillment.scheduler-delay-ms:5000}")
    public void processDueTasks() {
        fulfillmentService.recoverStaleProcessingTasks();
        fulfillmentService.processDueTasks();
    }
}
