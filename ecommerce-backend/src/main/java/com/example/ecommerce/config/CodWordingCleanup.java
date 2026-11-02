package com.example.ecommerce.config;

import com.example.ecommerce.service.StorePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * When cash on delivery is switched off, the sample customer quote seeded earlier ("Cash on delivery made it easy.")
 * would still promise it on the home page. This rewrites that one seeded sentence; quotes the owner typed are untouched.
 */
@Component
public class CodWordingCleanup {
    private static final Logger log = LoggerFactory.getLogger(CodWordingCleanup.class);
    private final JdbcTemplate jdbc;
    private final StorePolicy policy;

    public CodWordingCleanup(JdbcTemplate jdbc, StorePolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (policy.codEnabled()) return;
        try {
            int n = jdbc.update("UPDATE testimonials SET quote = REPLACE(quote, 'Cash on delivery made it easy.', 'Delivery was quick too.') "
                    + "WHERE quote LIKE '%Cash on delivery made it easy.%'");
            if (n > 0) log.info("Updated {} sample testimonial(s) that mentioned cash on delivery", n);
        } catch (Exception e) {
            log.warn("Could not update sample testimonials: {}", e.getMessage());
        }
    }
}
