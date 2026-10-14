package com.example.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
@EnableAsync
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
                System.out.println("Modified products columns successfully.");
            } catch (Exception e) {
                System.out.println("Failed to modify columns: " + e.getMessage());
            }
        };
    }
}
