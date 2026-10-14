package com.example.ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The single server-side price breakdown used by both the checkout preview and
 * order placement. total = subtotal - discount + tax + shipping.
 */
public record PriceQuote(
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxableAmount,
        BigDecimal tax,
        BigDecimal shipping,
        BigDecimal total,
        String currency) {

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
