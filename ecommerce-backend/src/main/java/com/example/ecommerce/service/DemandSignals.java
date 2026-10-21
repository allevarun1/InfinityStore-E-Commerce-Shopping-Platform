package com.example.ecommerce.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Honest social proof for the storefront: badges and counts that come from real orders and wishlists,
 * and only appear once the numbers are meaningful. Nothing is invented and nothing counts down.
 * The snapshot is cached briefly because it is shown on every product list.
 */
@Service
public class DemandSignals {
    public static final int MIN_SOLD = 3;
    public static final int MIN_SAVED = 3;
    public static final int WINDOW_DAYS = 30;

    public record Signal(String badge, int soldRecently, int savedBy) {
        public static final Signal NONE = new Signal(null, 0, 0);
    }

    private record Snapshot(long takenAt, Map<Long, Signal> byProduct) {}

    @Autowired private InsightsService insights;

    @Value("${app.signals.ttl-seconds:600}") private long ttlSeconds;

    private volatile Snapshot snapshot;

    public Signal forProduct(Long id) {
        return current().byProduct().getOrDefault(id, Signal.NONE);
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s != null && ttlSeconds > 0 && System.currentTimeMillis() - s.takenAt() < ttlSeconds * 1000) return s;
        synchronized (this) {
            s = snapshot;
            if (s != null && ttlSeconds > 0 && System.currentTimeMillis() - s.takenAt() < ttlSeconds * 1000) return s;
            snapshot = s = build();
            return s;
        }
    }

    private Snapshot build() {
        Map<Long, Integer> sold = insights.unitsSold(WINDOW_DAYS);
        Map<Long, Integer> saved = insights.wishlistCounts();
        // The three best sellers (with at least MIN_SOLD units) get the Bestseller badge.
        List<Long> top = sold.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_SOLD)
                .sorted(Map.Entry.<Long, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(3).map(Map.Entry::getKey).toList();
        Map<Long, Signal> out = new HashMap<>();
        java.util.Set<Long> ids = new java.util.HashSet<>(sold.keySet());
        ids.addAll(saved.keySet());
        for (Long id : ids) {
            int s = sold.getOrDefault(id, 0);
            int w = saved.getOrDefault(id, 0);
            String badge = top.contains(id) ? "BESTSELLER" : w >= MIN_SAVED ? "LOVED" : null;
            out.put(id, new Signal(badge, s >= MIN_SOLD ? s : 0, w >= MIN_SAVED ? w : 0));
        }
        return new Snapshot(System.currentTimeMillis(), out);
    }
}
