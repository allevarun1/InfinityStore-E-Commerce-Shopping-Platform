package com.example.ecommerce.service;

import com.example.ecommerce.repository.AuditLogRepository;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Keeps the free database small by removing data nobody needs any more, once a night (03:30 India time).
 * Customers, addresses, orders, payments, reviews and wishlists are never touched.
 */
@Service
public class StorageCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupScheduler.class);

    private final CartItemRepository carts;
    private final AuditLogRepository audit;
    private final UserRepository users;

    @Value("${app.cleanup.cart-days:120}")
    private int cartDays;

    @Value("${app.cleanup.audit-days:365}")
    private int auditDays;

    public StorageCleanupScheduler(CartItemRepository carts, AuditLogRepository audit, UserRepository users) {
        this.carts = carts;
        this.audit = audit;
        this.users = users;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 30 3 * * *}", zone = "Asia/Kolkata")
    public void nightly() {
        LocalDateTime now = LocalDateTime.now();
        int cartLines = step("old bag lines", () -> carts.deleteUntouchedSince(now.minusDays(cartDays)));
        int logs = step("old activity log entries", () -> audit.deleteOlderThanExceptOrders(now.minusDays(auditDays)));
        int codes = step("expired reset codes", () -> users.clearExpiredResetCodes(now));
        log.info("Nightly clean-up: removed {} bag line(s) untouched for {} days, {} activity entries older than {} days, {} expired reset code(s)",
                cartLines, cartDays, logs, auditDays, codes);
    }

    /** One failing step (for example the database waking up) never stops the others. */
    private static int step(String what, java.util.function.IntSupplier action) {
        try {
            return action.getAsInt();
        } catch (RuntimeException e) {
            log.warn("Nightly clean-up could not remove {}: {}", what, e.getMessage());
            return 0;
        }
    }
}
