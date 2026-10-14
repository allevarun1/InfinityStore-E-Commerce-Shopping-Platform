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

    public ProductView(Product product, int reviewCount, Double averageRating) {
        this.product = product;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore public Product getProduct() { return product; }
    public int getReviewCount() { return reviewCount; }
    public Double getAverageRating() { return averageRating; }
}
