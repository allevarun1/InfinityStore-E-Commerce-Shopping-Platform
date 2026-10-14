package com.example.ecommerce.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Review;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ReviewRepository;

@Service
public class ReviewService {
    public record ReviewView(Long id, int rating, String title, String body, String author, LocalDateTime createdAt) {}

    public record Eligibility(boolean eligible, String reason) {}

    @Autowired private ReviewRepository reviews;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private AuditService auditService;

    @Transactional(readOnly = true)
    public List<ReviewView> forProduct(Long productId) {
        productService.getProductById(productId);
        return reviews.findVisibleByProduct(productId).stream().map(ReviewService::view).toList();
    }

    @Transactional(readOnly = true)
    public Eligibility eligibility(User user, Long productId) {
        productService.getProductById(productId);
        if (reviews.existsByUserIdAndProductId(user.getId(), productId)) return new Eligibility(false, "ALREADY_REVIEWED");
        if (!orders.hasDelivered(user.getId(), productId)) return new Eligibility(false, "NOT_PURCHASED");
        return new Eligibility(true, "OK");
    }

    /** Only customers whose order for this product was delivered may review it, once. */
    @Transactional
    public ReviewView create(User user, Long productId, int rating, String title, String body) {
        Product product = productService.getProductById(productId);
        if (rating < 1 || rating > 5) throw new BusinessException("Rating must be between 1 and 5");
        Eligibility e = eligibility(user, productId);
        if (!e.eligible()) {
            throw new BusinessException(e.reason().equals("ALREADY_REVIEWED")
                    ? "You have already reviewed this product"
                    : "Only customers who received this product can review it");
        }
        try {
            Review saved = reviews.saveAndFlush(new Review(user, product, rating, clip(title, 120), clip(body, 2000)));
            return view(saved);
        } catch (DataIntegrityViolationException dup) {
            throw new BusinessException("You have already reviewed this product");
        }
    }

    @Transactional
    public void setHidden(Long reviewId, boolean hidden) {
        Review r = reviews.findById(reviewId).orElseThrow(() -> new BusinessException("Review not found"));
        r.setHidden(hidden);
        auditService.record(hidden ? "REVIEW_HIDDEN" : "REVIEW_RESTORED", "Review", reviewId, "product " + r.getProduct().getId());
    }

    private static ReviewView view(Review r) {
        return new ReviewView(r.getId(), r.getRating(), r.getTitle(), r.getBody(), author(r.getUser()), r.getCreatedAt());
    }

    /** First name and last initial only: reviews are public, so no full names or emails. */
    static String author(User u) {
        String first = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String last = u.getLastName() == null ? "" : u.getLastName().trim();
        if (first.isEmpty()) return "Customer";
        return last.isEmpty() ? first : first + " " + last.charAt(0) + ".";
    }

    private static String clip(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
