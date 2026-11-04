package com.example.ecommerce;

import com.example.ecommerce.dto.PriceQuote;
import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CouponRepository;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.CouponService;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PromoPopupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Coupons limited to chosen sarees, and pop-ups that hide when their code is switched off. */
class SelectedSareeCouponIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired OrderService orderService;
    @Autowired CouponService couponService;
    @Autowired CouponRepository coupons;
    @Autowired PromoPopupService popups;

    private Coupon coupon(String pct, String flat, Set<Long> on) {
        Coupon c = new Coupon();
        c.setCode("PICK" + N.incrementAndGet());
        if (pct != null) c.setPercentOff(new BigDecimal(pct));
        if (flat != null) c.setFlatOff(new BigDecimal(flat));
        c.setProductIds(on);
        return couponService.create(c);
    }

    private Product named(String price, String name) {
        Product p = product(price, 10);
        p.setName(name);
        return products.save(p);
    }

    @Test
    void codeForOneSareeOnlyDiscountsThatSaree() {
        User user = customer();
        Product a = named("1000.00", "Kanchi Silk A");
        Product b = named("1000.00", "Mysore Crepe B");
        addToCart(user, a, 1);
        addToCart(user, b, 1);
        Coupon c = coupon("10", null, Set.of(a.getId()));

        PriceQuote q = orderService.quoteCart(user, c.getCode());

        assertEquals(new BigDecimal("100.00"), q.couponDiscount(), "10% of the chosen saree only, not of the whole bag");
        PriceQuote.Line lineA = q.lines().stream().filter(l -> l.productId().equals(a.getId())).findFirst().orElseThrow();
        PriceQuote.Line lineB = q.lines().stream().filter(l -> l.productId().equals(b.getId())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("162.00"), lineA.lineTax(), "GST on the discounted saree");
        assertEquals(new BigDecimal("180.00"), lineB.lineTax(), "the other saree pays full price");
    }

    @Test
    void codeIsRefusedWithTheSareeNamesWhenNoneOfThemIsInTheBag() {
        User user = customer();
        Product a = named("1000.00", "Kanchi Silk A");
        Product b = named("1000.00", "Mysore Crepe B");
        addToCart(user, b, 1);
        Coupon c = coupon("10", null, Set.of(a.getId()));

        BusinessException e = assertThrows(BusinessException.class, () -> orderService.quoteCart(user, c.getCode()));
        assertTrue(e.getMessage().contains("Kanchi Silk A"), e.getMessage());
    }

    @Test
    void differentSareesCanHaveDifferentCodesAndOneCodeCanCoverTwoSarees() {
        Product a = named("1000.00", "Saree A");
        Product b = named("2000.00", "Saree B");
        Coupon forA = coupon("10", null, Set.of(a.getId()));
        Coupon forB = coupon(null, "300", Set.of(b.getId()));
        Coupon both = coupon("20", null, Set.of(a.getId(), b.getId()));

        User user = customer();
        addToCart(user, a, 1);
        addToCart(user, b, 1);
        assertEquals(new BigDecimal("100.00"), orderService.quoteCart(user, forA.getCode()).couponDiscount());
        assertEquals(new BigDecimal("300.00"), orderService.quoteCart(user, forB.getCode()).couponDiscount());
        assertEquals(new BigDecimal("600.00"), orderService.quoteCart(user, both.getCode()).couponDiscount());
    }

    @Test
    void minimumOrderAndFlatAmountAreMeasuredOnTheChosenSareesOnly() {
        User user = customer();
        Product a = named("400.00", "Small Saree");
        Product b = named("5000.00", "Big Saree");
        addToCart(user, a, 1);
        addToCart(user, b, 1);

        Coupon flat = coupon(null, "1000", Set.of(a.getId()));
        assertEquals(new BigDecimal("399.00"), orderService.quoteCart(user, flat.getCode()).couponDiscount(),
                "a flat code can't take more than the chosen saree costs");

        Coupon min = coupon("10", null, Set.of(a.getId()));
        min.setMinSubtotal(new BigDecimal("1000"));
        coupons.save(min);
        assertThrows(BusinessException.class, () -> orderService.quoteCart(user, min.getCode()),
                "the big saree in the bag does not count towards a code that is only for the small one");
    }

    @Test
    void emptySelectionStillMeansEverySaree() {
        User user = customer();
        addToCart(user, product("1000.00", 10), 1);
        addToCart(user, product("1000.00", 10), 1);
        Coupon c = coupon("10", null, Set.of());
        assertEquals(new BigDecimal("200.00"), orderService.quoteCart(user, c.getCode()).couponDiscount());
    }

    @Test
    void sareesCanBeChangedLaterAndUnknownSareesAreRefused() {
        Product a = named("1000.00", "Saree A");
        Coupon c = coupon("10", null, Set.of());
        assertEquals(Set.of(a.getId()), couponService.setProducts(c.getId(), List.of(a.getId())).getProductIds());
        assertTrue(couponService.setProducts(c.getId(), List.of()).getProductIds().isEmpty());
        assertThrows(BusinessException.class, () -> couponService.setProducts(c.getId(), List.of(987654321L)));
    }

    @Test
    void popUpHidesWhileItsCodeIsSwitchedOff() {
        Coupon c = coupon("10", null, Set.of());
        popups.create(new PromoPopupService.Input("Welcome", "Welcome " + c.getCode(), null, c.getCode(), null, null,
                null, "MAROON", "LOAD", "SESSION", 5, false, null, null, true));
        assertEquals(c.getCode(), popups.live().orElseThrow().couponCode());

        couponService.setActive(c.getId(), false);
        assertTrue(popups.live().map(p -> !c.getCode().equals(p.couponCode())).orElse(true), "switched-off code is not advertised");

        couponService.setActive(c.getId(), true);
        assertEquals(c.getCode(), popups.live().orElseThrow().couponCode());
    }
}
