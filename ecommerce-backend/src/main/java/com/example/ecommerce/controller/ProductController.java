package com.example.ecommerce.controller;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.AdminProductView;
import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.dto.ProductRequest;
import com.example.ecommerce.dto.ProductView;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.ProductService;

import jakarta.validation.Valid;

/**
 * Public reads return only published products. Everything that writes, and the admin catalogue
 * (which includes drafts, cost and supplier), requires the ADMIN role.
 */
@RestController
@RequestMapping("/api")
public class ProductController {
    private static final Pattern DATA_URL = Pattern.compile("(?s)^data:(image/(?:png|jpeg|jpg|webp|gif));base64,([A-Za-z0-9+/=]+)$");

    @Autowired
    private ProductService productService;

    // ------------------------------------------------------------ public

    @GetMapping("/products")
    public ResponseEntity<List<ProductView>> getAllProducts() {
        return ResponseEntity.ok(productService.views(productService.getAllProducts()));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductView> getProductById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(productService.view(productService.getProductById(id)));
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/products/category/{categoryId}")
    public ResponseEntity<List<ProductView>> getProductsByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.views(productService.getProductsByCategory(categoryId)));
    }

    @GetMapping("/products/search")
    public ResponseEntity<List<ProductView>> searchProducts(@RequestParam("q") String query) {
        return ResponseEntity.ok(productService.views(productService.searchProducts(query)));
    }

    @GetMapping("/products/deals")
    public ResponseEntity<List<ProductView>> getDeals() {
        return ResponseEntity.ok(productService.views(productService.getDeals()));
    }

    /** Serves an uploaded (inline) image as a cacheable file. The v parameter changes whenever the image does. */
    @GetMapping("/products/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        Product product;
        try {
            product = productService.getProductById(id);
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
        String stored = product.getImageUrl();
        Matcher m = stored == null ? null : DATA_URL.matcher(stored);
        if (m == null || !m.matches()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(m.group(1)))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(Base64.getDecoder().decode(m.group(2)));
    }

    // ------------------------------------------------------------ admin catalogue

    @GetMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminProductView>> adminList() {
        return ResponseEntity.ok(productService.adminList().stream().map(productService::adminView).toList());
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminProductView> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.adminView(productService.createProduct(request)));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminProductView> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.adminView(productService.updateProduct(id, request)));
    }

    @PutMapping("/products/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminProductView> setStatus(@PathVariable Long id, @RequestParam("to") String to) {
        return ResponseEntity.ok(productService.adminView(productService.transition(id, ProductService.parse(to))));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(new MessageResponse("Product deleted successfully!"));
    }

    @PutMapping("/products/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminProductView> markStock(@PathVariable Long id, @RequestParam boolean inStock) {
        return ResponseEntity.ok(productService.adminView(productService.markStock(id, inStock)));
    }
}
