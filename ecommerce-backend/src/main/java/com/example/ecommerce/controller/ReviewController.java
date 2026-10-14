package com.example.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.service.ReviewService;
import com.example.ecommerce.service.ReviewService.Eligibility;
import com.example.ecommerce.service.ReviewService.ReviewView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class ReviewController extends BaseUserController {
    public static class ReviewRequest {
        @Min(1) @Max(5) private int rating;
        @Size(max = 120) private String title;
        @Size(max = 2000) private String body;
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    @Autowired private ReviewService reviewService;

    /** Public (covered by the GET /api/products/** rule). */
    @GetMapping("/products/{id}/reviews")
    public ResponseEntity<List<ReviewView>> list(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.forProduct(id));
    }

    @GetMapping("/reviews/eligibility/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Eligibility> eligibility(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.eligibility(currentUser(), productId));
    }

    @PostMapping("/reviews/product/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewView> create(@PathVariable Long productId, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(reviewService.create(currentUser(), productId, request.getRating(), request.getTitle(), request.getBody()));
    }

    @PutMapping("/reviews/{id}/hidden")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> hide(@PathVariable Long id, @RequestParam boolean hidden) {
        reviewService.setHidden(id, hidden);
        return ResponseEntity.noContent().build();
    }
}
