package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A discount code. Exactly one of percentOff / flatOff is set. Applied after deal prices, before GST. */
@Entity
@Table(name = "coupons", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(length = 200)
    private String description;

    @Column(name = "percent_off", precision = 5, scale = 2)
    private BigDecimal percentOff;

    @Column(name = "flat_off", precision = 12, scale = 2)
    private BigDecimal flatOff;

    /** Upper limit on the discount a percentage coupon can give. */
    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    /** Minimum discounted subtotal (before GST) required to use the code. */
    @Column(name = "min_subtotal", precision = 12, scale = 2)
    private BigDecimal minSubtotal;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "first_order_only", nullable = false)
    private boolean firstOrderOnly;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Total redemptions allowed across all customers; null = unlimited. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPercentOff() { return percentOff; }
    public void setPercentOff(BigDecimal percentOff) { this.percentOff = percentOff; }
    public BigDecimal getFlatOff() { return flatOff; }
    public void setFlatOff(BigDecimal flatOff) { this.flatOff = flatOff; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public BigDecimal getMinSubtotal() { return minSubtotal; }
    public void setMinSubtotal(BigDecimal minSubtotal) { this.minSubtotal = minSubtotal; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isFirstOrderOnly() { return firstOrderOnly; }
    public void setFirstOrderOnly(boolean firstOrderOnly) { this.firstOrderOnly = firstOrderOnly; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
