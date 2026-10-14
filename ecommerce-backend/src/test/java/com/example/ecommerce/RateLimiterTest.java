package com.example.ecommerce;

import com.example.ecommerce.security.RateLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    @Test
    void blocksAfterLimitAndRecoversAfterWindow() {
        AtomicLong now = new AtomicLong(1_000_000);
        RateLimiter limiter = new RateLimiter(now::get);

        assertEquals(0, limiter.tryAcquire("ip", 2, 60_000));
        assertEquals(0, limiter.tryAcquire("ip", 2, 60_000));
        assertTrue(limiter.tryAcquire("ip", 2, 60_000) > 0, "third request in the window is rejected");

        assertEquals(0, limiter.tryAcquire("other-ip", 2, 60_000), "limits are per key");

        now.addAndGet(60_001);
        assertEquals(0, limiter.tryAcquire("ip", 2, 60_000), "a new window allows requests again");
    }
}
