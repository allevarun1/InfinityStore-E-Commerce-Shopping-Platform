package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A verified-purchase review: only customers with a delivered order of the product can write one. */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "uk_review_user_product", columnNames = {"user_id", "product_id"}))
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int rating;

    @Column(length = 120)
    private String title;

    @Column(length = 2000)
    private String body;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Review() {}

    public Review(User user, Product product, int rating, String title, String body) {
        this.user = user;
        this.product = product;
        this.rating = rating;
        this.title = title;
        this.body = body;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Product getProduct() { return product; }
    public int getRating() { return rating; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
