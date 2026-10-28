package com.store.common.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptGuardTest {
    @Test
    void blocksIpAfterEightFailedLogins() {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        for (int i=0;i<7;i++) assertEquals(0L,guard.recordFailure("198.51.100.10"));
        assertTrue(guard.recordFailure("198.51.100.10")>0L);
        assertTrue(guard.blockedForMillis("198.51.100.10")>0L);
    }
    @Test
    void successfulLoginClearsFailureHistory() {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        for(int i=0;i<7;i++) guard.recordFailure("198.51.100.10");
        guard.recordSuccess("198.51.100.10");
        for(int i=0;i<7;i++) assertEquals(0L,guard.recordFailure("198.51.100.10"));
        assertTrue(guard.recordFailure("198.51.100.10")>0L);
    }
    @Test
    void blocksAreIsolatedByIp() {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        for(int i=0;i<8;i++) guard.recordFailure("198.51.100.10");
        assertEquals(0L,guard.blockedForMillis("198.51.100.11"));
    }
}
