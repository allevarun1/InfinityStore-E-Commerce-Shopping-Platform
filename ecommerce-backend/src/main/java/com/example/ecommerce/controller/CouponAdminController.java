package com.example.ecommerce.controller;

import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.service.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Coupon management for store owners. */
@RestController
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class CouponAdminController {
    @Autowired
    private CouponService couponService;

    @Value("${app.growth.welcome-code:WELCOME10}")
    private String welcomeCode;

    public record CouponRequest(
            @NotBlank @Size(max = 40) String code,
            @Size(max = 200) String description,
            @DecimalMin("0.01") BigDecimal percentOff,
            @DecimalMin("0.01") BigDecimal flatOff,
            @DecimalMin("0.00") BigDecimal maxDiscount,
            @DecimalMin("0.00") BigDecimal minSubtotal,
            boolean firstOrderOnly,
            LocalDateTime expiresAt,
            Integer usageLimit,
            /* empty or missing = every saree */
            Set<Long> productIds) {
    }

    public record ProductsRequest(Set<Long> productIds) {
    }

    /** The welcome offer advertised in the pop-up, the top bar, saree pages and the newsletter box. */
    public record WelcomeView(String code, boolean exists, Long id, boolean active) {
    }

    public record CouponView(Long id, String code, String description, BigDecimal percentOff, BigDecimal flatOff,
                             BigDecimal maxDiscount, BigDecimal minSubtotal, boolean active, boolean firstOrderOnly,
                             LocalDateTime expiresAt, Integer usageLimit, int usedCount, List<Long> productIds) {
        static CouponView of(Coupon c) {
            return new CouponView(c.getId(), c.getCode(), c.getDescription(), c.getPercentOff(), c.getFlatOff(),
                    c.getMaxDiscount(), c.getMinSubtotal(), c.isActive(), c.isFirstOrderOnly(), c.getExpiresAt(),
                    c.getUsageLimit(), c.getUsedCount(), c.getProductIds().stream().sorted().toList());
        }
    }

    @GetMapping
    public ResponseEntity<List<CouponView>> list() {
        return ResponseEntity.ok(couponService.list().stream().map(CouponView::of).toList());
    }

    @PostMapping
    public ResponseEntity<CouponView> create(@Valid @RequestBody CouponRequest r) {
        Coupon c = new Coupon();
        c.setCode(r.code());
        c.setDescription(r.description());
        c.setPercentOff(r.percentOff());
        c.setFlatOff(r.flatOff());
        c.setMaxDiscount(r.maxDiscount());
        c.setMinSubtotal(r.minSubtotal());
        c.setFirstOrderOnly(r.firstOrderOnly());
        c.setExpiresAt(r.expiresAt());
        c.setUsageLimit(r.usageLimit());
        c.setProductIds(r.productIds());
        return ResponseEntity.ok(CouponView.of(couponService.create(c)));
    }

    @PutMapping("/{id}/products")
    public ResponseEntity<CouponView> setProducts(@PathVariable Long id, @RequestBody ProductsRequest r) {
        return ResponseEntity.ok(CouponView.of(couponService.setProducts(id, r.productIds())));
    }

    @GetMapping("/welcome")
    public ResponseEntity<WelcomeView> welcome() {
        String code = CouponService.normalize(welcomeCode);
        return ResponseEntity.ok(couponService.list().stream().filter(c -> c.getCode().equals(code)).findFirst()
                .map(c -> new WelcomeView(code, true, c.getId(), c.isActive()))
                .orElse(new WelcomeView(code, false, null, false)));
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<CouponView> setActive(@PathVariable Long id, @RequestParam boolean value) {
        return ResponseEntity.ok(CouponView.of(couponService.setActive(id, value)));
    }
}
