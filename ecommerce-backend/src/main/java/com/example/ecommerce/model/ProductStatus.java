package com.example.ecommerce.model;

/** Catalogue lifecycle. Only PUBLISHED products are visible to shoppers or purchasable. */
public enum ProductStatus {
    DRAFT, READY_FOR_REVIEW, APPROVED, PUBLISHED
}
