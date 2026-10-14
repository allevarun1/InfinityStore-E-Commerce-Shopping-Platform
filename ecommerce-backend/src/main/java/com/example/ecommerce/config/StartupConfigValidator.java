package com.example.ecommerce.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails start-up in a production environment when required configuration is missing or
 * obviously insecure. Messages name the setting, never its value.
 */
@Component
public class StartupConfigValidator {
    @Value("${app.env:production}")
    private String env;
    @Value("${app.jwt.secret:}")
    private String jwtSecret;
    @Value("${app.cors.allowed-origins:}")
    private String corsOrigins;
    @Value("${app.seed.admin.password:}")
    private String adminPassword;
    @Value("${app.payment.provider:none}")
    private String paymentProvider;

    @PostConstruct
    void validate() {
        if (!"production".equalsIgnoreCase(env)) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (jwtSecret.length() < 32 || jwtSecret.toLowerCase().contains("change-me")) {
            problems.add("JWT_SECRET must be a random value of at least 32 characters");
        }
        if (corsOrigins.isBlank()) {
            problems.add("CORS_ORIGINS must list the storefront origin(s)");
        } else if (java.util.Arrays.stream(corsOrigins.split(",")).map(String::trim).anyMatch("*"::equals)) {
            problems.add("CORS_ORIGINS must not be a wildcard");
        }
        if (adminPassword.length() < 10 || "admin123".equals(adminPassword)) {
            problems.add("ADMIN_PASSWORD must be at least 10 characters and not a default value");
        }
        if ("test".equalsIgnoreCase(paymentProvider)) {
            problems.add("PAYMENT_PROVIDER=test is not allowed in production");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", problems));
        }
    }
}
