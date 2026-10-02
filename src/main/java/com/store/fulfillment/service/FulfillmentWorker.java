package com.store.fulfillment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FulfillmentWorker {

    private final FulfillmentService fulfillmentService;

    @Scheduled(fixedDelay = 10000)
    public void poll() {
        fulfillmentService.recoverStaleProcessingTasks();
        fulfillmentService.processDueTasks();
    }
}
