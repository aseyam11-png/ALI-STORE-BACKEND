package com.store.fulfillment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FulfillmentWorker {

    private final FulfillmentService fulfillmentService;

    @Value("${app.fulfillment.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.fulfillment.poll-ms:10000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        fulfillmentService.recoverStaleProcessingTasks();
        fulfillmentService.processDueTasks();
    }
}
