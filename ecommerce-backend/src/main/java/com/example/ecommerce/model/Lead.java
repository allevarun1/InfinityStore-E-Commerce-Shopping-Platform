package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A person who asked to hear from the store (explicit opt-in): a newsletter sign-up, a "notify me when it
 * is back" request, or a contact request left with the shopping assistant. Either an email or a phone
 * number is always present. The owner works these in Admin > Leads.
 */
@Entity
@Table(name = "leads", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Lead {
    public static final String NEW = "NEW", CONTACTED = "CONTACTED", WON = "WON", LOST = "LOST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String name;

    @Column(length = 40)
    private String source;

    /** What they asked for, newest first, e.g. "red Banarasi under Rs. 5000 for a wedding". */
    @Column(length = 1000)
    private String interest;

    /** The saree they most recently asked about, if any. */
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(length = 12)
    private String status = NEW;

    @Column(length = 2000)
    private String notes;

    /** How many times they have raised their hand (sign-ups, requests, assistant contacts). */
    private Integer touches = 1;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Lead() {}

    public Lead(String email, String source) {
        this.email = email;
        this.source = source;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getInterest() { return interest; }
    public void setInterest(String interest) { this.interest = interest; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getStatus() { return status == null ? NEW : status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public int getTouches() { return touches == null ? 1 : touches; }
    public void setTouches(int touches) { this.touches = touches; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt == null ? createdAt : lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
