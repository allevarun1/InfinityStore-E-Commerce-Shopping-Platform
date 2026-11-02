package com.example.ecommerce.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Aiven may power off a free database it considers inactive. This switches it back on through the Aiven API:
 * once before the application starts (so a restart waits for the database instead of crashing) and every five
 * minutes while running. Needs AIVEN_API_TOKEN (an Aiven personal token); AIVEN_PROJECT and AIVEN_SERVICE default to
 * this shop's database. Without a token it does nothing.
 */
@Component
public class AivenPowerGuard {
    private static final Logger log = LoggerFactory.getLogger(AivenPowerGuard.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Duration START_WAIT = Duration.ofMinutes(12);

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static String token() {
        return env("AIVEN_API_TOKEN", "");
    }

    private static String serviceUrl() {
        return "https://api.aiven.io/v1/project/" + env("AIVEN_PROJECT", "allevarun057-aebf")
                + "/service/" + env("AIVEN_SERVICE", "rekha-mysql-free");
    }

    /** RUNNING, POWEROFF, REBUILDING, ... or null when the API could not be asked. */
    static String state() {
        try {
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(serviceUrl()))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "aivenv1 " + token()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Aiven API answered {} when checking the database", res.statusCode());
                return null;
            }
            JsonNode service = JSON.readTree(res.body()).path("service");
            return service.path("state").asText(null);
        } catch (Exception e) {
            log.warn("Could not ask Aiven about the database: {}", e.getMessage());
            return null;
        }
    }

    static boolean powerOn() {
        try {
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(serviceUrl()))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "aivenv1 " + token())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"powered\":true}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() / 100 == 2;
            if (ok) log.warn("The database was powered off by Aiven; switched it back on");
            else log.error("Aiven refused to power the database on: {} {}", res.statusCode(), res.body());
            return ok;
        } catch (Exception e) {
            log.error("Could not power the database on: {}", e.getMessage());
            return false;
        }
    }

    /** Before Spring starts: if the database is off, switch it on and wait (up to 12 minutes) until it runs. */
    public static void ensureRunningBeforeStart() {
        if (token().isEmpty()) return;
        String state = state();
        if (state == null || "RUNNING".equals(state)) return;
        if ("POWEROFF".equals(state)) powerOn();
        Instant until = Instant.now().plus(START_WAIT);
        while (Instant.now().isBefore(until)) {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            String now = state();
            if ("RUNNING".equals(now)) {
                log.info("Database is running again");
                return;
            }
            if ("POWEROFF".equals(now)) powerOn();
        }
        log.error("Database did not come back within {} minutes; starting anyway", START_WAIT.toMinutes());
    }

    /** While running: notice a power-off within five minutes and undo it. */
    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    public void keepPoweredOn() {
        if (token().isEmpty()) return;
        if ("POWEROFF".equals(state())) powerOn();
    }
}
