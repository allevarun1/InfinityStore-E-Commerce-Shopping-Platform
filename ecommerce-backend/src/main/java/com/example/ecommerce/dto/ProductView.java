package com.example.ecommerce.dto;

import com.example.ecommerce.model.Product;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Shopper-facing product: the catalogue fields plus real review statistics (absent when there are no reviews). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductView {
    @JsonUnwrapped
    private final Product product;
    private final int reviewCount;
    private final Double averageRating;
    private final String demandBadge;
    private final Integer soldRecently;
    private final Integer savedBy;

    public ProductView(Product product, int reviewCount, Double averageRating) {
        this(product, reviewCount, averageRating, com.example.ecommerce.service.DemandSignals.Signal.NONE);
    }

    /** Badge and counts come from real orders and wishlists and only appear once they are meaningful. */
    public ProductView(Product product, int reviewCount, Double averageRating, com.example.ecommerce.service.DemandSignals.Signal signal) {
        this.product = product;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        this.demandBadge = signal.badge();
        this.soldRecently = signal.soldRecently() > 0 ? signal.soldRecently() : null;
        this.savedBy = signal.savedBy() > 0 ? signal.savedBy() : null;
    }

    public String getDemandBadge() { return demandBadge; }
    public Integer getSoldRecently() { return soldRecently; }
    public Integer getSavedBy() { return savedBy; }

    @com.fasterxml.jackson.annotation.JsonIgnore public Product getProduct() { return product; }
    public int getReviewCount() { return reviewCount; }
    public Double getAverageRating() { return averageRating; }
}
