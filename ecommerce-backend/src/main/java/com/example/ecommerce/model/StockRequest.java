package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** "Tell me when this saree is back." Answered once by Autopilot when the product is in stock again. */
@Entity
@Table(name = "stock_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"lead_id", "product_id"}))
public class StockRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public StockRequest() {}

    public StockRequest(Lead lead, Long productId) {
        this.lead = lead;
        this.productId = productId;
    }

    public Long getId() { return id; }
    public Lead getLead() { return lead; }
    public Long getProductId() { return productId; }
    public LocalDateTime getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(LocalDateTime notifiedAt) { this.notifiedAt = notifiedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
