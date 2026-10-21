package com.example.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs Autopilot every 15 minutes by default. Disabled with AUTOMATION_ENABLED=false (and always in tests). */
@Component
@ConditionalOnProperty(name = "app.automation.enabled", havingValue = "true")
public class AutopilotScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutopilotScheduler.class);

    @Autowired private AutopilotService autopilot;

    @Scheduled(initialDelayString = "${app.automation.initial-delay-ms:120000}", fixedDelayString = "${app.automation.interval-ms:900000}")
    public void tick() {
        try {
            autopilot.runAll();
        } catch (Exception e) {
            // One bad run must not stop the schedule; the next tick tries again.
            log.error("Autopilot run failed: {}", e.getMessage());
        }
    }
}
