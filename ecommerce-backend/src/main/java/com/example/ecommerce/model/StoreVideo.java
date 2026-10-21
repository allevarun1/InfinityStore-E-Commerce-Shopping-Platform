package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A video the owner shows on the storefront: a wide "story" (how the sarees are made), a vertical "reel",
 * or a clip tied to one saree. The file itself lives elsewhere (a direct link or YouTube); only the link is stored.
 */
@Entity
@Table(name = "store_videos")
public class StoreVideo {
    public enum Kind { STORY, REEL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 240)
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind = Kind.REEL;

    /** Direct https link to an mp4/webm/mov file, or a YouTube link. */
    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
