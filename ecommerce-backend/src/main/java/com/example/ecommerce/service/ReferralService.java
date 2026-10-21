package com.example.ecommerce.service;

import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Refer a friend. Every customer gets a personal code (a coupon the friend can use on a first order).
 * When a friend's order that used the code is delivered, the referrer gets a single-use credit code that
 * only they can redeem. Built on ordinary coupons, so pricing, limits and cancellations behave as usual.
 */
@Service
@Transactional
public class ReferralService {
    static final String FRIEND_PREFIX = "FRIEND-";
    static final String REWARD_PREFIX = "THANKS-";
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final BigDecimal REWARD_RUPEES = new BigDecimal("200");

    public record Reward(String code, BigDecimal amount, boolean used, LocalDateTime expiresAt) {}
    public record Summary(String code, String friendOffer, String reward, int friendsRewarded, List<Reward> rewards) {}

    @Autowired private CouponRepository coupons;
    private final SecureRandom random = new SecureRandom();

    /** Marker stored in the coupon description: who owns a referral or reward code. */
    static String ownerTag(String kind, Long userId) { return kind + ":" + userId + ":"; }

    public static boolean isReferralCode(String code) {
        return code != null && code.toUpperCase().startsWith(FRIEND_PREFIX);
    }

    /** The user a personal code or reward belongs to, or null for an ordinary coupon. */
    public static Long ownerOf(Coupon c) {
        String d = c.getDescription();
        if (d == null) return null;
        for (String kind : new String[]{"REFERRAL", "REWARD"}) {
            if (d.startsWith(kind + ":")) {
                String[] parts = d.split(":");
                try { return Long.valueOf(parts[1]); } catch (RuntimeException e) { return null; }
            }
        }
        return null;
    }

    public static boolean isReward(Coupon c) {
        return c.getDescription() != null && c.getDescription().startsWith("REWARD:");
    }

    public Summary summary(User user) {
        Coupon mine = personalCode(user);
        List<Reward> rewards = coupons.findByDescriptionStartingWithOrderByCreatedAtDesc(ownerTag("REWARD", user.getId())).stream()
                .map(c -> new Reward(c.getCode(), c.getFlatOff(), c.getUsedCount() > 0, c.getExpiresAt())).toList();
        return new Summary(mine.getCode(), "10% off (up to Rs. 500) on their first order above Rs. 1,499",
                "Rs. " + REWARD_RUPEES.toBigInteger() + " off your next order", rewards.size(), rewards);
    }

    private Coupon personalCode(User user) {
        return coupons.findFirstByDescriptionStartingWith(ownerTag("REFERRAL", user.getId())).orElseGet(() -> {
            Coupon c = new Coupon();
            c.setCode(uniqueCode(FRIEND_PREFIX, 5));
            c.setDescription(ownerTag("REFERRAL", user.getId()) + "Friend offer");
            c.setPercentOff(new BigDecimal("10"));
            c.setMaxDiscount(new BigDecimal("500"));
            c.setMinSubtotal(new BigDecimal("1499"));
            c.setFirstOrderOnly(true);
            return coupons.save(c);
        });
    }

    /** Called when an order moves to DELIVERED. Pays the referrer once per order. */
    public void rewardReferrerFor(Order order) {
        if (!isReferralCode(order.getCouponCode())) return;
        coupons.findByCodeIgnoreCase(order.getCouponCode()).map(ReferralService::ownerOf).ifPresent(ownerId -> {
            if (ownerId.equals(order.getUser().getId())) return; // never reward yourself
            String tag = ownerTag("REWARD", ownerId) + "order " + order.getId();
            if (coupons.findFirstByDescriptionStartingWith(tag).isPresent()) return;
            Coupon r = new Coupon();
            r.setCode(uniqueCode(REWARD_PREFIX, 8));
            r.setDescription(tag);
            r.setFlatOff(REWARD_RUPEES);
            r.setMinSubtotal(new BigDecimal("1499"));
            r.setUsageLimit(1);
            r.setExpiresAt(LocalDateTime.now().plusDays(180));
            coupons.save(r);
        });
    }

    private String uniqueCode(String prefix, int len) {
        for (;;) {
            StringBuilder s = new StringBuilder(prefix);
            for (int i = 0; i < len; i++) s.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            if (coupons.findByCodeIgnoreCase(s.toString()).isEmpty()) return s.toString();
        }
    }
}
