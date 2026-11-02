package com.example.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EcommerceApplication {
    public static void main(String[] args) {
        // Aiven can power off the free database; switch it back on before connecting instead of crashing.
        com.example.ecommerce.config.AivenPowerGuard.ensureRunningBeforeStart();
        SpringApplication.run(EcommerceApplication.class, args);
    }

    @Bean
    @Profile("!test")
    public CommandLineRunner run(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE products MODIFY COLUMN image_url LONGTEXT;");
                jdbcTemplate.execute("ALTER TABLE products MODIFY COLUMN description LONGTEXT;");
                jdbcTemplate.execute("ALTER TABLE products MODIFY COLUMN name LONGTEXT;");
                try {
                    jdbcTemplate.execute("ALTER TABLE products ADD COLUMN deal_price DOUBLE;");
                } catch (Exception e) {
                    // Ignore if column already exists
                }
                try {
                    // Checkout allows addresses up to 500 characters; older databases created the column as VARCHAR(255).
                    jdbcTemplate.execute("ALTER TABLE orders MODIFY COLUMN shipping_address VARCHAR(500) NOT NULL;");
                } catch (Exception e) {
                    // Ignore if the table does not exist yet
                }
                try {
                    // Customers who sign in by mobile OTP may have no email; older databases created it NOT NULL.
                    jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN email VARCHAR(50) NULL;");
                } catch (Exception e) {
                    // Ignore if the table does not exist yet
                }
                try {
                    // Uploaded files now live in R2 when it is configured; older databases created data NOT NULL.
                    jdbcTemplate.execute("ALTER TABLE media_assets MODIFY COLUMN data LONGBLOB NULL;");
                } catch (Exception e) {
                    // Ignore if the table does not exist yet
                }
                try {
                    // Leads can now be phone-only (WhatsApp); older databases created the column NOT NULL.
                    jdbcTemplate.execute("ALTER TABLE leads MODIFY COLUMN email VARCHAR(120) NULL;");
                } catch (Exception e) {
                    // Ignore if the table does not exist yet or is already nullable
                }
                System.out.println("Modified products columns successfully.");
            } catch (Exception e) {
                System.out.println("Failed to modify columns: " + e.getMessage());
            }
        };
    }
}
