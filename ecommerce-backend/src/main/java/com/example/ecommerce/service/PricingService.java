package com.example.ecommerce.service;

import com.example.ecommerce.dto.PriceQuote;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative price calculation. Catalogue prices are tax-exclusive; GST is added on
 * top using the product's own rate or the configured default. Shipping is free above a
 * configurable threshold of the discounted subtotal.
 */
@Service
public class PricingService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal defaultGstPercent;
    private final BigDecimal freeShippingThreshold;
    private final BigDecimal shippingFee;
    private final String currency;
    private MultiBuyService multiBuy;

    /** Optional so the engine can still be built on its own in unit tests. */
    @Autowired(required = false)
    public void setMultiBuy(MultiBuyService multiBuy) {
        this.multiBuy = multiBuy;
    }

    @Autowired
    public PricingService(
            @Value("${app.pricing.default-gst-percent:18}") BigDecimal defaultGstPercent,
            @Value("${app.pricing.free-shipping-threshold:999}") BigDecimal freeShippingThreshold,
            @Value("${app.pricing.shipping-fee:49}") BigDecimal shippingFee,
            @Value("${app.pricing.currency:INR}") String currency) {
        this.defaultGstPercent = defaultGstPercent;
        this.freeShippingThreshold = freeShippingThreshold;
        this.shippingFee = shippingFee;
        this.currency = currency;
    }

    public static BigDecimal activePrice(Product product) {
        boolean onDeal = Boolean.TRUE.equals(product.getDeal()) && product.getDealPrice() != null
                && product.getDealPrice().compareTo(product.getPrice()) < 0;
        return onDeal ? product.getDealPrice() : product.getPrice();
    }

    public BigDecimal taxPercentFor(Product product) {
        return product.getGstPercent() != null ? product.getGstPercent() : defaultGstPercent;
    }

    public PriceQuote quote(List<CartItem> items) {
        return quote(items, BigDecimal.ZERO, null);
    }

    /**
     * Prices the cart with a coupon discount already validated by CouponService. The discount is
     * spread across lines in proportion to their value so GST is charged on what the customer pays.
     */
    public PriceQuote quote(List<CartItem> items, BigDecimal couponDiscount, String couponCode) {
        BigDecimal couponOnly = couponDiscount == null ? BigDecimal.ZERO : couponDiscount;
        BigDecimal netTotal = BigDecimal.ZERO;
        int itemCount = 0;
        for (CartItem item : items) {
            netTotal = netTotal.add(money(activePrice(item.getProduct())).multiply(BigDecimal.valueOf(item.getQuantity())));
            itemCount += item.getQuantity();
        }
        // Multi-buy tier first (percentage of the sale-price total), then the coupon on what is left.
        BigDecimal offer = BigDecimal.ZERO;
        String offerLabel = null;
        if (multiBuy != null && itemCount > 0) {
            var tier = multiBuy.bestFor(itemCount);
            if (tier.isPresent()) {
                offer = netTotal.multiply(tier.get().getPercentOff()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                offerLabel = tier.get().label();
            }
        }
        // The offer and coupon are spread across lines together so GST is charged on what the customer pays.
        BigDecimal coupon = offer.add(couponOnly);
        BigDecimal couponLeft = coupon;
        List<PriceQuote.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        for (int idx = 0; idx < items.size(); idx++) {
            CartItem item = items.get(idx);
            Product product = item.getProduct();
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            BigDecimal unit = money(activePrice(product));
            BigDecimal list = money(product.getPrice());
            BigDecimal taxPercent = taxPercentFor(product);
            BigDecimal lineNet = unit.multiply(qty);
            BigDecimal share = BigDecimal.ZERO;
            if (coupon.signum() > 0 && netTotal.signum() > 0) {
                share = idx == items.size() - 1 ? couponLeft
                        : coupon.multiply(lineNet).divide(netTotal, 2, RoundingMode.HALF_UP).min(couponLeft);
                couponLeft = couponLeft.subtract(share);
            }
            BigDecimal lineTax = lineNet.subtract(share).multiply(taxPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(list.multiply(qty));
            discount = discount.add(list.subtract(unit).multiply(qty));
            tax = tax.add(lineTax);
            lines.add(new PriceQuote.Line(item.getId(), product.getId(), product.getName(), product.getImageUrl(),
                    item.getQuantity(), list, unit, taxPercent, lineNet, lineTax));
        }

        BigDecimal taxable = subtotal.subtract(discount).subtract(coupon);
        BigDecimal shipping = (items.isEmpty() || taxable.compareTo(freeShippingThreshold) > 0)
                ? BigDecimal.ZERO.setScale(2) : money(shippingFee);
        BigDecimal total = taxable.add(tax).add(shipping);
        return new PriceQuote(lines, subtotal, discount, taxable, tax, shipping, total, currency,
                couponOnly.signum() > 0 ? couponCode : null, couponOnly, offer.signum() > 0 ? offerLabel : null, offer);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
