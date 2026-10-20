package com.example.ecommerce.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ProductRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    private String imageUrl;

    /** Extra photos in display order (http(s), site paths or uploaded data URLs). */
    @jakarta.validation.constraints.Size(max = 6, message = "Up to 6 extra photos")
    private java.util.List<String> gallery;

    public java.util.List<String> getGallery() { return gallery; }
    public void setGallery(java.util.List<String> gallery) { this.gallery = gallery; }

    @NotNull
    @Min(0)
    private Integer stockQuantity;

    @NotNull
    private Long categoryId;

    private Boolean deal = false;
    @DecimalMin("0.01")
    private BigDecimal dealPrice;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal gstPercent;
    @DecimalMin("0")
    @DecimalMax("5")
    private Double rating = 4.5;

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

    public String getImageUrl() {
        return imageUrl;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getDeal() {
        return deal;
    }

    public void setDeal(Boolean deal) {
        this.deal = deal;
    }

    public Double getRating() {
        return rating;
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

    @Size(max = 60) private String fabric;
    @Size(max = 60) private String weave;
    @Size(max = 60) private String occasion;
    @Size(max = 40) private String colour;
    @Size(max = 60) private String region;
    @Size(max = 500) private String careInstructions;
    private Boolean blouseIncluded;
    @DecimalMin("0") @DecimalMax("20") private BigDecimal lengthMeters;
    @DecimalMin("0") private BigDecimal costPrice;
    @Size(max = 120) private String supplierName;
    /** MANUAL (default) or AI_ASSISTED. */
    private String source;
    /** Optional lifecycle target applied after save; defaults to staying in DRAFT. */
    private String status;

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
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal v) { this.costPrice = v; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String v) { this.supplierName = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
