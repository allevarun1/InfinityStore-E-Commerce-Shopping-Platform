package com.example.ecommerce.model;

import java.math.BigDecimal;
import jakarta.persistence.Column;
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

    @Column(nullable = false)
    private Double rating = 4.5;

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

    // soft delete flag
    public Boolean getDeleted() {
    return deleted;
    }

    public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
    }
}
