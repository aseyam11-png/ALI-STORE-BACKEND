package com.store.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test (no Spring context) for the in-memory rate limiter backing
 * AuthRateLimitFilter's brute-force defense on /auth/login and /auth/register.
 * Deliberately not exercised through MockMvc against the real auth endpoints -
 * the limiter is a singleton shared across the whole test suite's Spring context,
 * so driving it through real HTTP calls would make unrelated auth tests flaky
 * depending on execution order and how many prior requests already consumed the window.
 */
class RateLimiterTest {

    @Test
    void tryConsume_allowsUpToLimitThenRejects() {
        RateLimiter limiter = new RateLimiter();
        String key = "203.0.113.1:/api/v1/auth/login";

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryConsume(key, 5, 60_000L), "request " + (i + 1) + " should be within the limit");
        }
        assertFalse(limiter.tryConsume(key, 5, 60_000L), "the 6th request in the same window must be rejected");
    }

    @Test
    void tryConsume_resetsAfterWindowExpires() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        String key = "203.0.113.2:/api/v1/auth/register";

        assertTrue(limiter.tryConsume(key, 1, 50L));
        assertFalse(limiter.tryConsume(key, 1, 50L));

        Thread.sleep(60L);

        assertTrue(limiter.tryConsume(key, 1, 50L), "a new window should allow requests again");
    }

    @Test
    void tryConsume_tracksDifferentKeysIndependently() {
        RateLimiter limiter = new RateLimiter();

        assertTrue(limiter.tryConsume("203.0.113.3:/api/v1/auth/login", 1, 60_000L));
        assertFalse(limiter.tryConsume("203.0.113.3:/api/v1/auth/login", 1, 60_000L));
        // A different client IP must not be blocked by another client's usage.
        assertTrue(limiter.tryConsume("203.0.113.4:/api/v1/auth/login", 1, 60_000L));
    }
}
