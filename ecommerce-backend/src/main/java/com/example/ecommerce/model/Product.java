package com.example.ecommerce.model;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100000)
    @Column(columnDefinition = "LONGTEXT")
    private String name;

    @NotBlank
    @Size(max = 100000)
    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @NotNull
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", columnDefinition = "LONGTEXT")
    private String imageUrl;

    /** Extra photos (JSON array of up to 6 URLs or uploaded data URLs), shown after the main image. */
    @Column(name = "gallery", columnDefinition = "LONGTEXT")
    private String galleryJson;

    @NotNull
    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(nullable = false)
    private Boolean deal = false;

    @Column(name = "deal_price", precision = 12, scale = 2)
    private BigDecimal dealPrice;

    // Optional per-product GST rate (percent). Null means use the configured default.
    @Column(name = "gst_percent", precision = 5, scale = 2)
    private BigDecimal gstPercent;

    // Legacy placeholder column. Never shown: real ratings come from verified reviews.
    @Column(nullable = false)
    private Double rating = 4.5;

    // Existing rows get PUBLISHED from the column default; null is also treated as published.
    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PUBLISHED'")
    private ProductStatus status = ProductStatus.DRAFT;

    // MANUAL or AI_ASSISTED. AI-assisted listings must pass every review step before publishing.
    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'MANUAL'")
    private String source = "MANUAL";

    @Column(length = 60) private String fabric;
    @Column(length = 60) private String weave;
    @Column(length = 60) private String occasion;
    @Column(length = 40) private String colour;
    @Column(length = 60) private String region;
    @Column(name = "care_instructions", length = 500) private String careInstructions;
    @Column(name = "blouse_included") private Boolean blouseIncluded;
    @Column(name = "length_meters", precision = 4, scale = 2) private BigDecimal lengthMeters;

    // Commercial data: never serialised to shoppers (see getters).
    @Column(name = "cost_price", precision = 12, scale = 2) private BigDecimal costPrice;
    @Column(name = "supplier_name", length = 120) private String supplierName;

    @Column(nullable = false)
    private Boolean deleted = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public Product() {}

    public Product(String name, String description, BigDecimal price, BigDecimal dealPrice, String imageUrl, Integer stockQuantity, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.dealPrice = dealPrice;
        this.imageUrl = imageUrl;
        this.stockQuantity = stockQuantity;
        this.category = category;
    }

    public Product(String name, String description, BigDecimal price, String imageUrl, Integer stockQuantity, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.stockQuantity = stockQuantity;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Boolean getDeal() {
        return deal;
    }

    public void setDeal(Boolean deal) {
        this.deal = deal;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public BigDecimal getDealPrice() {
        return dealPrice;
    }

    public void setDealPrice(BigDecimal dealPrice) {
        this.dealPrice = dealPrice;
    }

    public BigDecimal getGstPercent() { return gstPercent; }

    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }

    // soft delete flag
    public Boolean getDeleted() {
    return deleted;
    }

    public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
    }

    @JsonIgnore public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    @JsonIgnore public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    @JsonIgnore public Double getRating() { return rating; }
    @JsonIgnore public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public ProductStatus getStatus() { return status == null ? ProductStatus.PUBLISHED : status; }
    public void setStatus(ProductStatus status) { this.status = status; }
    @JsonIgnore public boolean isPublished() { return !Boolean.TRUE.equals(deleted) && getStatus() == ProductStatus.PUBLISHED; }
    public String getFabric() { return fabric; }
    public void setFabric(String v) { this.fabric = v; }
    public String getWeave() { return weave; }
    public void setWeave(String v) { this.weave = v; }
    public String getOccasion() { return occasion; }
    public void setOccasion(String v) { this.occasion = v; }
    public String getColour() { return colour; }
    public void setColour(String v) { this.colour = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
    public String getCareInstructions() { return careInstructions; }
    public void setCareInstructions(String v) { this.careInstructions = v; }
    public Boolean getBlouseIncluded() { return blouseIncluded; }
    public void setBlouseIncluded(Boolean v) { this.blouseIncluded = v; }
    public BigDecimal getLengthMeters() { return lengthMeters; }
    public void setLengthMeters(BigDecimal v) { this.lengthMeters = v; }

    private static final com.fasterxml.jackson.databind.ObjectMapper GALLERY_JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Stored gallery entries (may include uploaded data: URLs). Not serialised; see publicGallery(). */
    @JsonIgnore public java.util.List<String> getGalleryRaw() {
        if (galleryJson == null || galleryJson.isBlank()) return java.util.List.of();
        try {
            return java.util.List.of(GALLERY_JSON.readValue(galleryJson, String[].class));
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public void setGalleryRaw(java.util.List<String> urls) {
        try {
            this.galleryJson = urls == null || urls.isEmpty() ? null : GALLERY_JSON.writeValueAsString(urls);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Gallery as clients see it: uploaded pictures are served from a cacheable endpoint, like the main image. */
    @JsonProperty("gallery")
    public java.util.List<String> publicGallery() {
        java.util.List<String> raw = getGalleryRaw();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String u = raw.get(i);
            out.add(u.startsWith("data:") ? "/api/products/" + id + "/gallery/" + i + "?v=" + Integer.toHexString(u.hashCode()) : u);
        }
        return out;
    }

    /** Raw stored value (may be an uploaded data: URL). Not serialised; see publicImageUrl(). */
    @JsonIgnore public String getImageUrl() { return imageUrl; }

    /**
     * What clients receive. Uploaded images are stored inline as data URLs; serving them from
     * a cacheable endpoint keeps product lists small. The version suffix busts caches on change.
     */
    @JsonProperty("imageUrl")
    public String publicImageUrl() {
        if (imageUrl != null && imageUrl.startsWith("data:")) {
            return "/api/products/" + id + "/image?v=" + Integer.toHexString(imageUrl.hashCode());
        }
        return imageUrl;
    }
}
