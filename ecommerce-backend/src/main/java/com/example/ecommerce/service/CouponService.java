package com.example.ecommerce.service;

import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CouponRepository;
import com.example.ecommerce.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class CouponService {
    static final String GENERIC_INVALID = "This code is not valid";

    @Autowired
    private CouponRepository coupons;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private AuditService audit;

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    public record Applied(Coupon coupon, BigDecimal discount) {
    }

    /**
     * Validates the code for this customer and basket and returns the discount in rupees.
     * Pass lock=true when the redemption is about to be recorded (order placement).
     */
    public Applied evaluate(User user, String rawCode, BigDecimal discountedSubtotal, boolean lock) {
        String code = normalize(rawCode);
        if (code.isEmpty()) {
            throw new BusinessException("Enter a coupon code");
        }
        Coupon c = (lock ? coupons.lockByCode(code) : coupons.findByCodeIgnoreCase(code))
                .orElseThrow(() -> new BusinessException(GENERIC_INVALID));
        Long owner = ReferralService.ownerOf(c);
        if (owner != null && ReferralService.isReward(c) && !owner.equals(user.getId())) {
            throw new BusinessException(GENERIC_INVALID); // credit belongs to the referrer only
        }
        if (owner != null && !ReferralService.isReward(c) && owner.equals(user.getId())) {
            throw new BusinessException("You can't use your own referral code. Share it with a friend instead");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!c.isActive() || (c.getStartsAt() != null && c.getStartsAt().isAfter(now))) {
            throw new BusinessException(GENERIC_INVALID);
        }
        if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(now)) {
            throw new BusinessException("This code has expired");
        }
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) {
            throw new BusinessException("This code has been fully redeemed");
        }
        if (c.isFirstOrderOnly() && orders.countActiveByUser(user.getId()) > 0) {
            throw new BusinessException("This code is only valid on your first order");
        }
        if (orders.countActiveByUserAndCoupon(user.getId(), c.getCode()) > 0) {
            throw new BusinessException("You have already used this code");
        }
        if (c.getMinSubtotal() != null && discountedSubtotal.compareTo(c.getMinSubtotal()) < 0) {
            throw new BusinessException("Add items worth Rs. " + c.getMinSubtotal().setScale(0, RoundingMode.CEILING)
                    + " or more to use this code");
        }
        BigDecimal off;
        if (c.getPercentOff() != null) {
            off = discountedSubtotal.multiply(c.getPercentOff()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (c.getMaxDiscount() != null) {
                off = off.min(c.getMaxDiscount());
            }
        } else {
            off = c.getFlatOff() == null ? BigDecimal.ZERO : c.getFlatOff();
        }
        // Never discount below zero; keep at least Rs. 1 payable so the order is still a real sale.
        off = off.min(discountedSubtotal.subtract(BigDecimal.ONE).max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
        return new Applied(c, off);
    }

    public void recordRedemption(Coupon c) {
        c.setUsedCount(c.getUsedCount() + 1);
        coupons.save(c);
    }

    /** Called when an order that used a coupon is cancelled, so the code can be used again. */
    public void releaseRedemption(String code) {
        coupons.lockByCode(code).ifPresent(c -> {
            c.setUsedCount(Math.max(0, c.getUsedCount() - 1));
            coupons.save(c);
        });
    }

    @Transactional(readOnly = true)
    public List<Coupon> list() {
        return coupons.findAllByOrderByCreatedAtDesc();
    }

    public Coupon create(Coupon c) {
        c.setCode(normalize(c.getCode()));
        if (!c.getCode().matches("[A-Z0-9_-]{3,40}")) {
            throw new BusinessException("Code must be 3-40 letters, digits, - or _");
        }
        boolean pct = c.getPercentOff() != null;
        boolean flat = c.getFlatOff() != null;
        if (pct == flat) {
            throw new BusinessException("Set either a percentage or a flat amount");
        }
        if (pct && (c.getPercentOff().signum() <= 0 || c.getPercentOff().compareTo(new BigDecimal("90")) > 0)) {
            throw new BusinessException("Percentage must be between 1 and 90");
        }
        if (flat && c.getFlatOff().signum() <= 0) {
            throw new BusinessException("Flat amount must be positive");
        }
        if (c.getUsageLimit() != null && c.getUsageLimit() < 1) {
            throw new BusinessException("Usage limit must be at least 1");
        }
        if (coupons.findByCodeIgnoreCase(c.getCode()).isPresent()) {
            throw new BusinessException("A coupon with this code already exists");
        }
        Coupon saved = coupons.save(c);
        audit.record("COUPON_CREATED", "COUPON", saved.getId(), saved.getCode());
        return saved;
    }

    public Coupon setActive(Long id, boolean active) {
        Coupon c = coupons.findById(id).orElseThrow(() -> new BusinessException("Coupon not found"));
        c.setActive(active);
        audit.record("COUPON_ACTIVE_CHANGED", "COUPON", id, c.getCode() + " active=" + active);
        return coupons.save(c);
    }

    /** A coupon that is currently usable by anyone, used to show the welcome offer after sign-up. */
    @Transactional(readOnly = true)
    public Optional<Coupon> publicOffer(String code) {
        return coupons.findByCodeIgnoreCase(normalize(code)).filter(Coupon::isActive)
                .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(c -> c.getUsageLimit() == null || c.getUsedCount() < c.getUsageLimit());
    }
}
