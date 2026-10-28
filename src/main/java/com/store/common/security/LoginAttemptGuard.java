package com.store.common.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-process failed-login lockout complementary to account lockout and rate limiting. */
@Component
public class LoginAttemptGuard {

    private static final int MAX_FAILURES = 8;
    private static final long FAILURE_WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final long BLOCK_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final long EVICT_AFTER_MILLIS = Duration.ofMinutes(30).toMillis();

    private final Map<String, State> states = new ConcurrentHashMap<>();

    public long blockedForMillis(String clientIp) {
        long now = System.currentTimeMillis();
        State state = states.get(normalizeKey(clientIp));
        if (state == null) return 0L;

        synchronized (state) {
            state.lastTouchedAt = now;
            if (state.blockedUntil > now) return state.blockedUntil - now;
            if (state.blockedUntil != 0L) {
                state.blockedUntil = 0L;
                state.failures.clear();
            }
            removeExpiredFailures(state, now);
            return 0L;
        }
    }

    public long recordFailure(String clientIp) {
        long now = System.currentTimeMillis();
        String key = normalizeKey(clientIp);
        State state = states.computeIfAbsent(key, ignored -> new State(now));

        synchronized (state) {
            state.lastTouchedAt = now;
            if (state.blockedUntil > now) return state.blockedUntil - now;
            if (state.blockedUntil != 0L) {
                state.blockedUntil = 0L;
                state.failures.clear();
            }
            removeExpiredFailures(state, now);
            state.failures.addLast(now);
            if (state.failures.size() >= MAX_FAILURES) {
                state.blockedUntil = now + BLOCK_MILLIS;
                return BLOCK_MILLIS;
            }
            return 0L;
        }
    }

    public void recordSuccess(String clientIp) {
        State state = states.get(normalizeKey(clientIp));
        if (state == null) return;
        synchronized (state) {
            state.failures.clear();
            state.blockedUntil = 0L;
            state.lastTouchedAt = System.currentTimeMillis();
        }
    }

    private void removeExpiredFailures(State state, long now) {
        while (!state.failures.isEmpty()
                && now - state.failures.peekFirst() > FAILURE_WINDOW_MILLIS) {
            state.failures.removeFirst();
        }
    }

    private String normalizeKey(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    @Scheduled(fixedRate = 5 * 60 * 1000L)
    void cleanup() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(entry -> {
            State state = entry.getValue();
            synchronized (state) {
                return state.blockedUntil <= now
                        && now - state.lastTouchedAt > EVICT_AFTER_MILLIS;
            }
        });
    }

    private static final class State {
        private final ArrayDeque<Long> failures = new ArrayDeque<>();
        private long blockedUntil;
        private long lastTouchedAt;

        private State(long now) {
            this.lastTouchedAt = now;
        }
    }
}
