package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A visitor who asked to hear from the store (explicit opt-in). */
@Entity
@Table(name = "leads", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 40)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Lead() {}

    public Lead(String email, String source) {
        this.email = email;
        this.source = source;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getSource() { return source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
