package com.example.ecommerce.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.ProductRequest;
import com.example.ecommerce.model.Category;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    // ✅ GET ALL ACTIVE PRODUCTS ONLY
    public List<Product> getAllProducts() {
        return productRepository.findByDeletedFalse();
    }

    // ✅ GET PRODUCT BY ID (ONLY IF NOT DELETED)
    public Product getProductById(Long id) {
        return productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new com.example.ecommerce.service.BusinessException("Product not found with id: " + id));
    }

    // ✅ GET BY CATEGORY
    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryIdAndDeletedFalse(categoryId);
    }

    // ✅ SEARCH PRODUCTS
    public List<Product> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return productRepository.findByDeletedFalse();
        }
        return productRepository.findByNameContainingIgnoreCaseAndDeletedFalse(query);
    }

    // ✅ CREATE PRODUCT
    public Product createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new com.example.ecommerce.service.BusinessException("Category not found with id: " + request.getCategoryId()));

        Product product = new Product(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getDealPrice(),
                request.getImageUrl(),
                request.getStockQuantity(),
                category
        );

        validatePricing(request);
        product.setGstPercent(request.getGstPercent());
        product.setDeal(Boolean.TRUE.equals(request.getDeal()));
        product.setRating(request.getRating() == null ? 4.5 : request.getRating());
        product.setDeleted(false);

        return productRepository.save(product);
    }

    private void validatePricing(ProductRequest request) {
        if (Boolean.TRUE.equals(request.getDeal())
                && (request.getDealPrice() == null || request.getDealPrice().compareTo(request.getPrice()) >= 0)) {
            throw new BusinessException("A deal needs a deal price lower than the regular price");
        }
    }

    // ✅ UPDATE PRODUCT
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = getProductById(id);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new com.example.ecommerce.service.BusinessException("Category not found with id: " + request.getCategoryId()));

        validatePricing(request);
        product.setGstPercent(request.getGstPercent());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setDealPrice(request.getDealPrice());
        product.setImageUrl(request.getImageUrl());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(category);
        product.setDeal(Boolean.TRUE.equals(request.getDeal()));
        product.setRating(request.getRating() == null ? product.getRating() : request.getRating());

        return productRepository.save(product);
    }

    // ✅ GET DEALS ONLY (ACTIVE)
    public List<Product> getDeals() {
        return productRepository.findByDealTrueAndDeletedFalse();
    }

    // ✅ STOCK UPDATE
    public Product markStock(Long id, boolean inStock) {
        Product product = getProductById(id);

        if (!inStock) {
            product.setStockQuantity(0);
        } else if (product.getStockQuantity() == null || product.getStockQuantity() == 0) {
            product.setStockQuantity(10);
        }

        return productRepository.save(product);
    }

    // ✅ SOFT DELETE (NO FK ERRORS EVER)
    @Transactional
    public void deleteProduct(Long id) {

        Product product = getProductById(id);

        // optional cleanup for cart
        cartItemRepository.deleteByProductId(id);

        // soft delete instead of hard delete
        product.setDeleted(true);
        productRepository.save(product);
    }
}