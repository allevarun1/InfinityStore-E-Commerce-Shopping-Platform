package com.example.ecommerce.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Fixed-window, in-memory request counter. Per application instance. */
public class RateLimiter {
    private static final class Window {
        long start;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier clockMillis;

    public RateLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public RateLimiter() {
        this(System::currentTimeMillis);
    }

    /** Returns 0 if the request is allowed, otherwise the number of seconds until the window resets. */
    public long tryAcquire(String key, int limit, long windowMillis) {
        long now = clockMillis.getAsLong();
        if (windows.size() > 20_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMillis * 2);
        }
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            if (now - w.start >= windowMillis) {
                w.start = now;
                w.count = 0;
            }
            if (w.count >= limit) {
                return Math.max(1, (w.start + windowMillis - now + 999) / 1000);
            }
            w.count++;
            return 0;
        }
    }
}
