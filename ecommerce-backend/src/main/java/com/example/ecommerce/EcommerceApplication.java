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
