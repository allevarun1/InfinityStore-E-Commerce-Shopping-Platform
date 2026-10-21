package com.example.ecommerce;

import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CouponRepository;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.ReferralService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ReferralIntegrationTest extends IntegrationTestSupport {
    @Autowired OrderService orderService;
    @Autowired ReferralService referrals;
    @Autowired CouponRepository coupons;

    @Test
    void everyCustomerGetsOneStablePersonalCode() {
        User a = customer();
        User b = customer();
        String code = referrals.summary(a).code();
        assertTrue(code.startsWith("FRIEND-"));
        assertEquals(code, referrals.summary(a).code());
        assertNotEquals(code, referrals.summary(b).code());
    }

    @Test
    void friendGetsDiscountAndReferrerEarnsCreditOnlyOnDelivery() {
        User referrer = customer();
        User friend = customer();
        String code = referrals.summary(referrer).code();

        addToCart(friend, product("2000.00", 10), 1);
        var r = request("COD");
        r.setCouponCode(code);
        Order o = orderService.placeOrder(friend, r, null);
        assertEquals(new BigDecimal("200.00"), o.getCouponDiscountAmount());
        assertEquals(0, referrals.summary(referrer).rewards().size(), "no credit before the saree arrives");

        orderService.updateOrderStatus(o.getId(), "SHIPPED");
        assertEquals(0, referrals.summary(referrer).rewards().size());
        orderService.updateOrderStatus(o.getId(), "DELIVERED");
        var rewards = referrals.summary(referrer).rewards();
        assertEquals(1, rewards.size());
        assertEquals(new BigDecimal("200.00"), rewards.get(0).amount());
        assertEquals(0, referrals.summary(friend).rewards().size());
    }

    @Test
    void cancelledFriendOrderEarnsNothingAndOwnCodeIsRefused() {
        User referrer = customer();
        User friend = customer();
        String code = referrals.summary(referrer).code();

        addToCart(referrer, product("2000.00", 10), 1);
        assertThrows(BusinessException.class, () -> orderService.quoteCart(referrer, code));

        addToCart(friend, product("2000.00", 10), 1);
        var r = request("COD");
        r.setCouponCode(code);
        Order o = orderService.placeOrder(friend, r, null);
        orderService.updateOrderStatus(o.getId(), "CANCELLED");
        assertEquals(0, referrals.summary(referrer).rewards().size());
    }

    @Test
    void creditCodeWorksOnlyForTheReferrerAndOnlyOnce() {
        User referrer = customer();
        User friend = customer();
        User stranger = customer();
        String code = referrals.summary(referrer).code();
        addToCart(friend, product("2000.00", 10), 1);
        var r = request("COD");
        r.setCouponCode(code);
        Order o = orderService.placeOrder(friend, r, null);
        orderService.updateOrderStatus(o.getId(), "SHIPPED");
        orderService.updateOrderStatus(o.getId(), "DELIVERED");
        String credit = referrals.summary(referrer).rewards().get(0).code();

        addToCart(stranger, product("2000.00", 10), 1);
        assertThrows(BusinessException.class, () -> orderService.quoteCart(stranger, credit));

        addToCart(referrer, product("2000.00", 10), 1);
        assertEquals(new BigDecimal("200.00"), orderService.quoteCart(referrer, credit).couponDiscount());
        var r2 = request("COD");
        r2.setCouponCode(credit);
        orderService.placeOrder(referrer, r2, null);
        Coupon c = coupons.findByCodeIgnoreCase(credit).orElseThrow();
        assertEquals(1, c.getUsedCount());
        assertTrue(referrals.summary(referrer).rewards().get(0).used());
    }
}
