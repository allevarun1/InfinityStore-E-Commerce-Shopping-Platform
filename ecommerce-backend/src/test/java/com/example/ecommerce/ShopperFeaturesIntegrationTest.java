package com.example.ecommerce;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.PriceQuote;
import com.example.ecommerce.model.*;
import com.example.ecommerce.repository.BundleOfferRepository;
import com.example.ecommerce.repository.CouponRepository;
import com.example.ecommerce.repository.WishlistItemRepository;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.InsightsService;
import com.example.ecommerce.service.LeadService;
import com.example.ecommerce.service.MultiBuyService;
import com.example.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Multi-buy offers, delivery estimates, public order tracking, demand badges and personal-shopping leads. */
class ShopperFeaturesIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orderService;
    @Autowired MultiBuyService multiBuy;
    @Autowired BundleOfferRepository offerRepo;
    @Autowired CouponRepository coupons;
    @Autowired WishlistItemRepository wishlist;
    @Autowired InsightsService insights;
    @Autowired LeadService leadService;

    /** Offers are shared state; leave none behind for the other test classes. */
    @AfterEach
    void cleanOffers() {
        offerRepo.deleteAll();
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ multi-buy

    @Test
    void bestTierAppliesBeforeTheCouponAndGstIsChargedOnWhatIsPaid() {
        multiBuy.create(2, new BigDecimal("5"));
        multiBuy.create(3, new BigDecimal("10"));
        User u = customer();
        addToCart(u, product("1000.00", 10), 1);

        PriceQuote one = orderService.quoteCart(u);
        assertEquals(0, BigDecimal.ZERO.compareTo(one.offerDiscount()), "one saree does not qualify");
        assertNull(one.offerLabel());

        addToCart(u, product("1000.00", 10), 1);
        PriceQuote two = orderService.quoteCart(u);
        assertEquals(new BigDecimal("100.00"), two.offerDiscount()); // 5% of 2000
        assertEquals("Buy 2+ and save 5%", two.offerLabel());
        assertEquals(new BigDecimal("1900.00"), two.taxableAmount());
        assertEquals(new BigDecimal("342.00"), two.tax()); // 18% of 1900
        assertEquals(new BigDecimal("2242.00"), two.total()); // free shipping above 999

        Coupon c = new Coupon();
        c.setCode("STACK" + N.incrementAndGet());
        c.setPercentOff(new BigDecimal("10"));
        coupons.save(c);
        PriceQuote stacked = orderService.quoteCart(u, c.getCode());
        assertEquals(new BigDecimal("190.00"), stacked.couponDiscount(), "the coupon is taken from the already-discounted total");
        assertEquals(new BigDecimal("100.00"), stacked.offerDiscount());
        assertEquals(new BigDecimal("1710.00"), stacked.taxableAmount());
        assertEquals(new BigDecimal("307.80"), stacked.tax());
        assertEquals(new BigDecimal("2017.80"), stacked.total());

        addToCart(u, product("1000.00", 10), 1);
        assertEquals("Buy 3+ and save 10%", orderService.quoteCart(u).offerLabel(), "the best qualifying tier wins");
    }

    @Test
    void offerIsRecordedOnTheOrderAndTheExpectedTotalGuardStillHolds() {
        multiBuy.create(2, new BigDecimal("10"));
        User u = customer();
        Product a = product("2000.00", 5);
        Product b = product("1000.00", 5);
        addToCart(u, a, 1);
        addToCart(u, b, 1);
        PriceQuote q = orderService.quoteCart(u);
        assertEquals(new BigDecimal("300.00"), q.offerDiscount());

        OrderRequest wrong = request("COD");
        wrong.setExpectedTotal(q.total().add(BigDecimal.ONE));
        assertThrows(BusinessException.class, () -> orderService.placeOrder(u, wrong, "k-" + N.incrementAndGet()));

        BigDecimal netBefore = insights.dashboard(1).netSales();
        OrderRequest ok = request("COD");
        ok.setExpectedTotal(q.total());
        Order o = orderService.placeOrder(u, ok, "k-" + N.incrementAndGet());
        assertEquals(new BigDecimal("300.00"), o.getOfferDiscountAmount());
        assertEquals("Buy 2+ and save 10%", o.getOfferLabel());
        assertEquals(0, q.total().compareTo(o.getTotalPrice()));
        assertEquals(4, stockOf(a), "stock is still reduced exactly once per item");
        assertEquals(4, stockOf(b));

        // The owner's sales figure is what was actually earned, not the list total.
        assertEquals(0, new BigDecimal("2700.00").compareTo(insights.dashboard(1).netSales().subtract(netBefore)));
    }

    @Test
    void ownerManagesTiersWithSaneLimits() throws Exception {
        assertThrows(BusinessException.class, () -> multiBuy.create(1, new BigDecimal("5")));
        assertThrows(BusinessException.class, () -> multiBuy.create(3, new BigDecimal("80")));
        var t = multiBuy.create(4, new BigDecimal("12.5"));
        assertThrows(BusinessException.class, () -> multiBuy.create(4, new BigDecimal("5")), "one offer per item count");
        assertEquals(1, body(mvc.perform(get("/api/store/offers"))).size());
        multiBuy.setActive(t.getId(), false);
        assertEquals(0, body(mvc.perform(get("/api/store/offers"))).size(), "inactive tiers are not shown to shoppers");
        mvc.perform(get("/api/admin/offers")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ delivery estimate

    @Test
    void deliveryEstimateUsesTheConfiguredZones() throws Exception {
        JsonNode metro = body(mvc.perform(get("/api/store/delivery").param("pincode", "560001")).andExpect(status().isOk()));
        assertTrue(metro.get("serviceable").asBoolean());
        assertEquals("metro", metro.get("zone").asText());
        assertEquals(LocalDate.now().plusDays(1 + 3).toString(), metro.get("earliest").asText(), "one dispatch day plus 3 days in transit");
        assertTrue(metro.get("message").asText().startsWith("Estimated delivery"));

        assertEquals("remote", body(mvc.perform(get("/api/store/delivery").param("pincode", "190001"))).get("zone").asText());
        assertEquals("standard", body(mvc.perform(get("/api/store/delivery").param("pincode", "624001"))).get("zone").asText());
        mvc.perform(get("/api/store/delivery").param("pincode", "56001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/store/delivery").param("pincode", "060001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/store/delivery").param("pincode", "abcdef")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ tracking

    @Test
    void trackingNeedsTheOrderNumberAndTheMatchingMobileAndRevealsNoAddress() throws Exception {
        User u = customer();
        Product p = product("1500.00", 5);
        addToCart(u, p, 2);
        Order o = orderService.placeOrder(u, request("COD"), "k-" + N.incrementAndGet());

        var ok = mvc.perform(post("/api/store/track").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("orderNumber", o.getTrackingNumber().toLowerCase(), "phone", "+91 98765-43210"))))
                .andExpect(status().isOk());
        JsonNode r = body(ok);
        assertEquals("PENDING", r.get("status").asText());
        assertEquals(2, r.get("items").get(0).get("quantity").asInt());
        assertFalse(r.toString().contains("Test Street"), "the delivery address is never returned");
        assertFalse(r.has("shippingAddress"));

        String wrongPhone = mvc.perform(post("/api/store/track").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("orderNumber", o.getTrackingNumber(), "phone", "9000000000"))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String wrongOrder = mvc.perform(post("/api/store/track").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("orderNumber", "INF-00000000-ZZZZ", "phone", "9876543210"))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertEquals(wrongPhone, wrongOrder, "a miss never says which detail was wrong");
    }

    // ------------------------------------------------------------------ demand badges

    @Test
    void badgesAppearOnlyFromRealSalesAndWishlistsAndNeverBelowTheThreshold() throws Exception {
        Product quiet = product("900.00", 10);
        JsonNode none = body(mvc.perform(get("/api/products/" + quiet.getId())));
        assertFalse(none.has("demandBadge"));
        assertFalse(none.has("soldRecently"));

        User buyer = customer();
        Product hot = product("1200.00", 40);
        carts.save(new CartItem(buyer, hot, 9));
        orderService.placeOrder(buyer, request("COD"), "k-" + N.incrementAndGet());
        JsonNode badge = body(mvc.perform(get("/api/products/" + hot.getId())));
        assertEquals("BESTSELLER", badge.get("demandBadge").asText());
        assertEquals(9, badge.get("soldRecently").asInt());

        Product loved = product("1100.00", 5);
        for (int i = 0; i < 3; i++) wishlist.save(new WishlistItem(customer(), loved));
        JsonNode lovedView = body(mvc.perform(get("/api/products/" + loved.getId())));
        assertEquals("LOVED", lovedView.get("demandBadge").asText());
        assertEquals(3, lovedView.get("savedBy").asInt());
    }

    // ------------------------------------------------------------------ personal shopping leads

    @Test
    void videoCallBookingBecomesAHotLeadWithAReadyReply() throws Exception {
        String phone = "97" + String.format("%08d", N.incrementAndGet());
        mvc.perform(post("/api/store/leads").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Divya", "phone", phone, "source", "video_call",
                        "interest", "Video call, Saturday evening: bridal silk under 20k", "consent", true))))
                .andExpect(status().isOk());
        LeadService.LeadView v = leadService.list().stream().filter(l -> phone.equals(l.phone())).findFirst().orElseThrow();
        assertEquals("hot", v.temperature(), v.reasons().toString());
        assertTrue(v.reasons().contains("booked a video shopping call"));
        assertTrue(v.followUp().contains("video call"));
        assertTrue(v.whatsappLink().startsWith("https://wa.me/91" + phone));
    }
}
