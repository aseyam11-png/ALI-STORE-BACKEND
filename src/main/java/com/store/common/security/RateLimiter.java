package com.store.common.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory fixed-window rate limiter. Deliberately not backed by Redis/Hazelcast -
 * this is a single-instance modular monolith, and adding distributed rate-limiting
 * infrastructure would be over-engineering for the current deployment shape. If the
 * app is ever horizontally scaled, this must move to a shared store (e.g. Redis) since
 * each instance would otherwise track its own independent counters.
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart >= windowMillis) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return window.count.get() <= maxRequests;
    }

    // Evict windows that are well past expiry so memory doesn't grow unboundedly
    // across many distinct client IPs over long process uptime.
    @Scheduled(fixedRate = 5 * 60 * 1000)
    void cleanup() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart > 10 * 60 * 1000);
    }

    private static final class Window {
        final long windowStart;
        final AtomicInteger count;

        Window(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
