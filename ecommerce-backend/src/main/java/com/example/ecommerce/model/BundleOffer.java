package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A multi-buy tier: "buy {minItems} or more sarees, take {percentOff}% off". The best tier the basket
 * qualifies for is applied automatically, before any coupon.
 */
@Entity
@Table(name = "bundle_offers", uniqueConstraints = @UniqueConstraint(columnNames = "min_items"))
public class BundleOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_items", nullable = false)
    private int minItems;

    @Column(name = "percent_off", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentOff;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public BundleOffer() {}

    public BundleOffer(int minItems, BigDecimal percentOff) {
        this.minItems = minItems;
        this.percentOff = percentOff;
    }

    public Long getId() { return id; }
    public int getMinItems() { return minItems; }
    public void setMinItems(int minItems) { this.minItems = minItems; }
    public BigDecimal getPercentOff() { return percentOff; }
    public void setPercentOff(BigDecimal percentOff) { this.percentOff = percentOff; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String label() {
        return "Buy " + minItems + "+ and save " + percentOff.stripTrailingZeros().toPlainString() + "%";
    }
}
