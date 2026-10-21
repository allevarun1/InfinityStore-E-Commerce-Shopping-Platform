package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A customer quote the owner chooses to feature. These are written by the owner from real feedback;
 * sample quotes (local demo only) are flagged so the storefront labels them as such.
 */
@Entity
@Table(name = "testimonials")
public class Testimonial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 60)
    private String location;

    @Column(nullable = false, length = 300)
    private String quote;

    @Column(nullable = false)
    private int rating = 5;

    @Column(name = "product_id")
    private Long productId;

    /** True for placeholder copy that ships with the local demo; shown with a "Sample" label. */
    @Column(nullable = false)
    private boolean sample;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getQuote() { return quote; }
    public void setQuote(String quote) { this.quote = quote; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public boolean isSample() { return sample; }
    public void setSample(boolean sample) { this.sample = sample; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
