package com.store.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptGuardTest {

    @Test
    void blocksIpAfterEightFailedLoginsAndExposesRetryTime() {
        LoginAttemptGuard guard = new LoginAttemptGuard();

        for (int attempt = 1; attempt < 8; attempt++) {
            assertEquals(0L, guard.recordFailure("198.51.100.10"));
        }

        long blockedFor = guard.recordFailure("198.51.100.10");
        assertTrue(blockedFor > 0L);
        assertTrue(guard.blockedForMillis("198.51.100.10") > 0L);
    }

    @Test
    void doesNotBlockAnUnrelatedIp() {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        for (int attempt = 0; attempt < 8; attempt++) {
            guard.recordFailure("198.51.100.10");
        }

        assertEquals(0L, guard.blockedForMillis("198.51.100.11"));
    }

    @Test
    void successfulLoginResetsFailureHistoryForThatIp() {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        for (int attempt = 0; attempt < 7; attempt++) {
            guard.recordFailure("198.51.100.13");
        }

        guard.recordSuccess("198.51.100.13");
        for (int attempt = 0; attempt < 7; attempt++) {
            assertEquals(0L, guard.recordFailure("198.51.100.13"));
        }
        assertTrue(guard.recordFailure("198.51.100.13") > 0L);
    }

    @Test
    void countsFailuresWithinTheSameWindowEvenWhenRequestsAreConcurrent() throws Exception {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> guard.recordFailure("198.51.100.12"));
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(guard.blockedForMillis("198.51.100.12") > 0L);
    }
}
