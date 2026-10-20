package com.example.ecommerce;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.PriceQuote;
import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CouponRepository;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired OrderService orderService;
    @Autowired CouponRepository coupons;
    @Autowired MockMvc mvc;

    private Coupon percent(String pct, String max, String min, boolean firstOnly) {
        Coupon c = new Coupon();
        c.setCode("TEST" + N.incrementAndGet());
        c.setPercentOff(new BigDecimal(pct));
        if (max != null) c.setMaxDiscount(new BigDecimal(max));
        if (min != null) c.setMinSubtotal(new BigDecimal(min));
        c.setFirstOrderOnly(firstOnly);
        return coupons.save(c);
    }

    private OrderRequest withCoupon(String code) {
        OrderRequest r = request("COD");
        r.setCouponCode(code);
        return r;
    }

    @Test
    void percentCouponReducesTaxableAmountAndGstIsChargedOnTheDiscountedPrice() {
        User user = customer();
        Product p = product("1000.00", 10);
        addToCart(user, p, 1);
        Coupon c = percent("10", null, null, false);

        PriceQuote q = orderService.quoteCart(user, c.getCode().toLowerCase());

        assertEquals(new BigDecimal("100.00"), q.couponDiscount());
        assertEquals(c.getCode(), q.couponCode());
        assertEquals(new BigDecimal("900.00"), q.taxableAmount());
        assertEquals(new BigDecimal("162.00"), q.tax()); // 18% of 900
        assertEquals(new BigDecimal("49.00"), q.shipping()); // 900 is not above 999
        assertEquals(new BigDecimal("1111.00"), q.total());
    }

    @Test
    void maxDiscountCapsAPercentageCoupon() {
        User user = customer();
        addToCart(user, product("5000.00", 10), 1);
        Coupon c = percent("20", "300", null, false);
        assertEquals(new BigDecimal("300.00"), orderService.quoteCart(user, c.getCode()).couponDiscount());
    }

    @Test
    void discountIsSplitAcrossLinesWithoutLosingARupee() {
        User user = customer();
        addToCart(user, product("333.33", 10), 1);
        addToCart(user, product("666.67", 10), 1);
        Coupon c = percent("10", null, null, false);
        PriceQuote q = orderService.quoteCart(user, c.getCode());
        assertEquals(new BigDecimal("100.00"), q.couponDiscount());
        assertEquals(new BigDecimal("900.00"), q.taxableAmount());
    }

    @Test
    void invalidExpiredInactiveAndBelowMinimumCodesAreRefused() {
        User user = customer();
        addToCart(user, product("500.00", 10), 1);

        assertThrows(BusinessException.class, () -> orderService.quoteCart(user, "NOPE"));

        Coupon expired = percent("10", null, null, false);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        coupons.save(expired);
        assertThrows(BusinessException.class, () -> orderService.quoteCart(user, expired.getCode()));

        Coupon off = percent("10", null, null, false);
        off.setActive(false);
        coupons.save(off);
        assertThrows(BusinessException.class, () -> orderService.quoteCart(user, off.getCode()));

        Coupon big = percent("10", null, "5000", false);
        assertThrows(BusinessException.class, () -> orderService.quoteCart(user, big.getCode()));
    }

    @Test
    void orderStoresCouponAndRedemptionIsCountedOnceAndReleasedOnCancel() {
        User user = customer();
        addToCart(user, product("1000.00", 10), 1);
        Coupon c = percent("10", null, null, false);

        Order o = orderService.placeOrder(user, withCoupon(c.getCode()), null);

        assertEquals(c.getCode(), o.getCouponCode());
        assertEquals(new BigDecimal("100.00"), o.getCouponDiscountAmount());
        assertEquals(new BigDecimal("1111.00"), o.getTotalPrice());
        assertEquals(1, coupons.findById(c.getId()).orElseThrow().getUsedCount());

        // the same customer cannot reuse the code
        addToCart(user, product("1000.00", 10), 1);
        assertThrows(BusinessException.class, () -> orderService.placeOrder(user, withCoupon(c.getCode()), null));

        orderService.cancelByCustomer(user, o.getId());
        assertEquals(0, coupons.findById(c.getId()).orElseThrow().getUsedCount());
    }

    @Test
    void usageLimitIsEnforced() {
        Coupon c = percent("10", null, null, false);
        c.setUsageLimit(1);
        coupons.save(c);

        User first = customer();
        addToCart(first, product("1000.00", 10), 1);
        orderService.placeOrder(first, withCoupon(c.getCode()), null);

        User second = customer();
        addToCart(second, product("1000.00", 10), 1);
        assertThrows(BusinessException.class, () -> orderService.placeOrder(second, withCoupon(c.getCode()), null));
    }

    @Test
    void firstOrderOnlyCouponIsRefusedForReturningCustomers() {
        User user = customer();
        addToCart(user, product("1000.00", 10), 1);
        orderService.placeOrder(user, request("COD"), null);

        addToCart(user, product("1000.00", 10), 1);
        Coupon c = percent("10", null, null, true);
        assertThrows(BusinessException.class, () -> orderService.quoteCart(user, c.getCode()));
    }

    @Test
    void newsletterSignupIsPublicIdempotentAndShowsTheWelcomeOffer() throws Exception {
        Coupon welcome = coupons.findByCodeIgnoreCase("WELCOME10").orElseGet(() -> {
            Coupon c = new Coupon();
            c.setCode("WELCOME10");
            c.setPercentOff(new BigDecimal("10"));
            return coupons.save(c);
        });
        assertTrue(welcome.isActive());

        String body = "{\"email\":\"Shopper@Example.com\",\"source\":\"footer\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/store/subscribe").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offer.code").value("WELCOME10"));
        }
        mvc.perform(post("/api/store/subscribe").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }
}
