package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * An offer pop-up the owner designs in the admin (like the welcome and sale pop-ups on large shopping sites).
 * Shoppers see at most one at a time: the newest active one that is inside its schedule.
 */
@Entity
@Table(name = "promo_popups")
public class PromoPopup {
    public enum Theme { MAROON, GOLD, EMERALD, NIGHT }
    public enum Trigger { LOAD, EXIT }
    public enum Frequency { SESSION, DAY, ALWAYS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The owner's own label, never shown to shoppers. */
    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 80)
    private String headline;

    @Column(length = 240)
    private String message;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "cta_label", length = 30)
    private String ctaLabel;

    @Column(name = "cta_url", length = 200)
    private String ctaUrl;

    /** A library file (/api/media/...), an https link, or a site path. */
    @Column(name = "image_url", length = 300)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Theme theme = Theme.MAROON;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_on", nullable = false, length = 10)
    private Trigger trigger = Trigger.LOAD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency = Frequency.SESSION;

    @Column(name = "delay_seconds", nullable = false)
    private int delaySeconds = 5;

    /** Shows a live countdown to endsAt. */
    @Column(nullable = false)
    private boolean countdown;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getCtaLabel() { return ctaLabel; }
    public void setCtaLabel(String ctaLabel) { this.ctaLabel = ctaLabel; }
    public String getCtaUrl() { return ctaUrl; }
    public void setCtaUrl(String ctaUrl) { this.ctaUrl = ctaUrl; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme; }
    public Trigger getTrigger() { return trigger; }
    public void setTrigger(Trigger trigger) { this.trigger = trigger; }
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public int getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(int delaySeconds) { this.delaySeconds = delaySeconds; }
    public boolean isCountdown() { return countdown; }
    public void setCountdown(boolean countdown) { this.countdown = countdown; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = LocalDateTime.now(); }
}
