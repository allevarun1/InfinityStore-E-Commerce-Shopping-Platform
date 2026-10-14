package com.example.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-username lockout after repeated failed sign-ins. State is per application
 * instance and resets on restart; move to a shared store if the API is scaled horizontally.
 */
@Service
public class LoginAttemptService {
    private record State(int failures, long lockedUntilMillis, long lastFailureMillis) {
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    @Value("${app.login.max-failures:8}")
    private int maxFailures;

    @Value("${app.login.lock-minutes:15}")
    private long lockMinutes;

    public void checkAllowed(String username) {
        State s = states.get(key(username));
        long now = System.currentTimeMillis();
        if (s != null && s.lockedUntilMillis() > now) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Try again later.",
                    (s.lockedUntilMillis() - now + 999) / 1000);
        }
    }

    public void recordFailure(String username) {
        long now = System.currentTimeMillis();
        long lockMillis = lockMinutes * 60_000L;
        states.merge(key(username), new State(1, 0, now), (old, ignored) -> {
            int failures = (old.lockedUntilMillis() != 0 && old.lockedUntilMillis() <= now) ? 1 : old.failures() + 1;
            long lockedUntil = failures >= maxFailures ? now + lockMillis : 0;
            return new State(failures, lockedUntil, now);
        });
        if (states.size() > 10_000) {
            states.entrySet().removeIf(e -> now - e.getValue().lastFailureMillis() > lockMillis);
        }
    }

    public void recordSuccess(String username) {
        states.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
