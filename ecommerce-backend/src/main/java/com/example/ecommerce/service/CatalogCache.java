package com.example.ecommerce.service;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Keeps the public catalog (saree list, saree pages, categories) in memory, because the database is a long round trip
 * away and these pages are read on every visit. Any change to a product, category or review (admin edits, stock
 * changing after an order, a new review) clears it at once via the entity listener below; otherwise entries live for
 * at most a minute so live signals such as "x people viewing" stay fresh.
 */
@Component
public class CatalogCache {
    static final Duration TTL = Duration.ofSeconds(60);
    private static final int MAX_ENTRIES = 2000;
    private static final AtomicLong VERSION = new AtomicLong();

    private record Entry(Object value, long version, long expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> load) {
        long now = System.currentTimeMillis();
        long version = VERSION.get();
        Entry e = entries.get(key);
        if (e != null && e.version() == version && e.expiresAt() > now) {
            return (T) e.value();
        }
        T value = load.get();
        // Only keep it if nothing changed while it was loading; otherwise the next reader reloads.
        if (VERSION.get() == version) {
            if (entries.size() >= MAX_ENTRIES) entries.clear();
            entries.put(key, new Entry(value, version, now + TTL.toMillis()));
        }
        return value;
    }

    /** Drops everything; the next request reads fresh data from the database. */
    public static void invalidate() {
        VERSION.incrementAndGet();
    }

    /** Attached to Product, Category and Review so every saved change clears the cache. */
    public static class Listener {
        @PostPersist
        @PostUpdate
        @PostRemove
        void changed(Object entity) {
            invalidate();
            // Again once the change is committed, so a page read in between cannot be kept.
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                invalidate();
                            }
                        });
            }
        }
    }
}
