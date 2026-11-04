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
 * Authoritative price calculation. The price the owner enters is the final price the customer pays: no GST is
 * added (app.pricing.default-gst-percent defaults to 0; set DEFAULT_GST_PERCENT only after registering for GST). Shipping is free above a
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
            @Value("${app.pricing.default-gst-percent:0}") BigDecimal defaultGstPercent,
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
        // One store-wide rate (0 by default). Per-saree GST rates are no longer used.
        return defaultGstPercent;
    }

    public PriceQuote quote(List<CartItem> items) {
        return quote(items, BigDecimal.ZERO, null);
    }

    /**
     * Prices the cart with a coupon discount already validated by CouponService. The discount is
     * spread across lines in proportion to their value so GST is charged on what the customer pays.
     */
    public PriceQuote quote(List<CartItem> items, BigDecimal couponDiscount, String couponCode) {
        return quote(items, couponDiscount, couponCode, null);
    }

    /** As above, for a coupon that only covers some sarees: its discount is spread over those lines only. */
    public PriceQuote quote(List<CartItem> items, BigDecimal couponDiscount, String couponCode, java.util.Set<Long> couponProducts) {
        BigDecimal couponOnly = couponDiscount == null ? BigDecimal.ZERO : couponDiscount;
        java.util.function.Predicate<CartItem> covered = it -> couponProducts == null || couponProducts.isEmpty()
                || couponProducts.contains(it.getProduct().getId());
        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal coveredTotal = BigDecimal.ZERO;
        int lastCovered = -1;
        int itemCount = 0;
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            BigDecimal net = money(activePrice(item.getProduct())).multiply(BigDecimal.valueOf(item.getQuantity()));
            netTotal = netTotal.add(net);
            if (covered.test(item)) {
                coveredTotal = coveredTotal.add(net);
                lastCovered = i;
            }
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
        // The offer (all lines) and the coupon (covered lines) are spread so GST is charged on what the customer pays.
        BigDecimal coupon = offer.add(couponOnly);
        BigDecimal offerLeft = offer;
        BigDecimal couponLeft = couponOnly;
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
            if (offer.signum() > 0 && netTotal.signum() > 0) {
                BigDecimal part = idx == items.size() - 1 ? offerLeft
                        : offer.multiply(lineNet).divide(netTotal, 2, RoundingMode.HALF_UP).min(offerLeft);
                offerLeft = offerLeft.subtract(part);
                share = share.add(part);
            }
            if (couponOnly.signum() > 0 && coveredTotal.signum() > 0 && covered.test(item)) {
                BigDecimal part = idx == lastCovered ? couponLeft
                        : couponOnly.multiply(lineNet).divide(coveredTotal, 2, RoundingMode.HALF_UP).min(couponLeft);
                couponLeft = couponLeft.subtract(part);
                share = share.add(part);
            }
            BigDecimal lineTax = lineNet.subtract(share).multiply(taxPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(list.multiply(qty));
            discount = discount.add(list.subtract(unit).multiply(qty));
            tax = tax.add(lineTax);
            lines.add(new PriceQuote.Line(item.getId(), product.getId(), product.getName(), product.getImageUrl(),
                    item.getQuantity(), list, unit, taxPercent, lineNet, lineTax));
        }

        BigDecimal taxable = subtotal.subtract(discount).subtract(coupon);
        // A threshold of 0 (or less) means the shop has no free shipping: every order pays the flat fee.
        boolean freeOver = freeShippingThreshold != null && freeShippingThreshold.signum() > 0
                && taxable.compareTo(freeShippingThreshold) > 0;
        BigDecimal shipping = (items.isEmpty() || freeOver)
                ? BigDecimal.ZERO.setScale(2) : money(shippingFee);
        BigDecimal total = taxable.add(tax).add(shipping);
        return new PriceQuote(lines, subtotal, discount, taxable, tax, shipping, total, currency,
                couponOnly.signum() > 0 ? couponCode : null, couponOnly, offer.signum() > 0 ? offerLabel : null, offer);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
