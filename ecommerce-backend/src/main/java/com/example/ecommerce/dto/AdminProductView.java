package com.example.ecommerce.dto;

import com.example.ecommerce.model.Product;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.math.BigDecimal;
import java.util.List;

/** Admin-only product: adds cost, supplier, margin, lifecycle source and publish-readiness. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminProductView {
    @JsonUnwrapped
    private final Product product;
    private final BigDecimal costPrice;
    private final String supplierName;
    private final String source;
    private final Double marginPercent;
    private final List<String> readinessIssues;
    private final String previewImage;
    private final List<String> previewGallery;

    public AdminProductView(Product product, Double marginPercent, List<String> readinessIssues) {
        this.product = product;
        this.costPrice = product.getCostPrice();
        this.supplierName = product.getSupplierName();
        this.source = product.getSource();
        this.marginPercent = marginPercent;
        this.readinessIssues = readinessIssues;
        // Unpublished images are not served publicly, so admins preview the stored value directly.
        String raw = product.getImageUrl();
        this.previewImage = !product.isPublished() && raw != null && raw.startsWith("data:") ? raw : null;
        this.previewGallery = !product.isPublished() && product.getGalleryRaw().stream().anyMatch(u -> u.startsWith("data:"))
                ? product.getGalleryRaw() : null;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore public Product getProduct() { return product; }
    public BigDecimal getCostPrice() { return costPrice; }
    public String getSupplierName() { return supplierName; }
    public String getSource() { return source; }
    public Double getMarginPercent() { return marginPercent; }
    public List<String> getReadinessIssues() { return readinessIssues; }
    public String getPreviewImage() { return previewImage; }
    public List<String> getPreviewGallery() { return previewGallery; }
}
