package com.example.ecommerce;

import com.example.ecommerce.dto.PriceQuote;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.service.PricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingServiceTest {
    private final PricingService pricing = new PricingService(
            new BigDecimal("18"), new BigDecimal("999"), new BigDecimal("49"), "INR");

    private static Product product(String price, String dealPrice, boolean deal, String gst) {
        Product p = new Product();
        p.setId(1L);
        p.setName("Test saree");
        p.setPrice(new BigDecimal(price));
        p.setDealPrice(dealPrice == null ? null : new BigDecimal(dealPrice));
        p.setDeal(deal);
        p.setGstPercent(gst == null ? null : new BigDecimal(gst));
        return p;
    }

    private static CartItem item(Product p, int qty) {
        CartItem ci = new CartItem();
        ci.setProduct(p);
        ci.setQuantity(qty);
        return ci;
    }

    @Test
    void addsDefaultGstAndShippingBelowThreshold() {
        PriceQuote q = pricing.quote(List.of(item(product("500.00", null, false, null), 1)));
        assertEquals(new BigDecimal("500.00"), q.subtotal());
        assertEquals(new BigDecimal("90.00"), q.tax());
        assertEquals(new BigDecimal("49.00"), q.shipping());
        assertEquals(new BigDecimal("639.00"), q.total());
    }

    @Test
    void shippingIsFreeAboveThreshold() {
        PriceQuote q = pricing.quote(List.of(item(product("1000.00", null, false, null), 1)));
        assertEquals(new BigDecimal("0.00"), q.shipping());
        assertEquals(new BigDecimal("1180.00"), q.total());
    }

    @Test
    void thresholdIsExclusive() {
        PriceQuote q = pricing.quote(List.of(item(product("999.00", null, false, null), 1)));
        assertEquals(new BigDecimal("49.00"), q.shipping());
    }

    @Test
    void dealPriceIsAppliedAndReportedAsDiscount() {
        PriceQuote q = pricing.quote(List.of(item(product("12499.00", "9999.00", true, null), 2)));
        assertEquals(new BigDecimal("24998.00"), q.subtotal());
        assertEquals(new BigDecimal("5000.00"), q.discount());
        assertEquals(new BigDecimal("19998.00"), q.taxableAmount());
        assertEquals(new BigDecimal("3599.64"), q.tax());
        assertEquals(new BigDecimal("23597.64"), q.total());
    }

    @Test
    void dealFlagWithoutLowerDealPriceDoesNotDiscount() {
        PriceQuote q = pricing.quote(List.of(item(product("2000.00", "2500.00", true, null), 1)));
        assertEquals(new BigDecimal("0.00"), q.discount());
        assertEquals(new BigDecimal("2000.00"), q.taxableAmount());
    }

    @Test
    void perProductTaxRateOverridesDefault() {
        PriceQuote q = pricing.quote(List.of(item(product("2000.00", null, false, "5"), 1)));
        assertEquals(new BigDecimal("100.00"), q.tax());
        assertEquals(new BigDecimal("2100.00"), q.total());
    }

    @Test
    void roundsTaxToTwoDecimalsWithoutFloatingPointDrift() {
        // 3 x 33.33 = 99.99 ; 18% = 17.9982 -> 18.00
        PriceQuote q = pricing.quote(List.of(item(product("33.33", null, false, null), 3)));
        assertEquals(new BigDecimal("18.00"), q.tax());
        assertEquals(new BigDecimal("166.99"), q.total());
    }

    @Test
    void emptyCartHasNoShippingOrTotal() {
        PriceQuote q = pricing.quote(List.of());
        assertEquals(0, q.total().compareTo(BigDecimal.ZERO));
        assertEquals(0, q.shipping().compareTo(BigDecimal.ZERO));
    }
}
