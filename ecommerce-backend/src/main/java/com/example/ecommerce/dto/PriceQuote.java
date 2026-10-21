package com.example.ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The single server-side price breakdown used by both the checkout preview and
 * order placement. total = subtotal - discount - couponDiscount - offerDiscount + tax + shipping.
 */
public record PriceQuote(
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxableAmount,
        BigDecimal tax,
        BigDecimal shipping,
        BigDecimal total,
        String currency,
        String couponCode,
        BigDecimal couponDiscount,
        String offerLabel,
        BigDecimal offerDiscount) {

    /** Quote with a coupon but no multi-buy offer. */
    public PriceQuote(List<Line> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal taxableAmount,
                      BigDecimal tax, BigDecimal shipping, BigDecimal total, String currency,
                      String couponCode, BigDecimal couponDiscount) {
        this(lines, subtotal, discount, taxableAmount, tax, shipping, total, currency, couponCode, couponDiscount,
                null, BigDecimal.ZERO);
    }

    /** Quote without a coupon. */
    public PriceQuote(List<Line> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal taxableAmount,
                      BigDecimal tax, BigDecimal shipping, BigDecimal total, String currency) {
        this(lines, subtotal, discount, taxableAmount, tax, shipping, total, currency, null, BigDecimal.ZERO);
    }

    public record Line(
            Long cartItemId,
            Long productId,
            String name,
            String imageUrl,
            int quantity,
            BigDecimal listUnitPrice,
            BigDecimal unitPrice,
            BigDecimal taxPercent,
            BigDecimal lineNet,
            BigDecimal lineTax) {
    }
}
