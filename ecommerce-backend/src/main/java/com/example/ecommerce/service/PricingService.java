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
        List<PriceQuote.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product product = item.getProduct();
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            BigDecimal unit = money(activePrice(product));
            BigDecimal list = money(product.getPrice());
            BigDecimal taxPercent = taxPercentFor(product);
            BigDecimal lineNet = unit.multiply(qty);
            BigDecimal lineTax = lineNet.multiply(taxPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(list.multiply(qty));
            discount = discount.add(list.subtract(unit).multiply(qty));
            tax = tax.add(lineTax);
            lines.add(new PriceQuote.Line(item.getId(), product.getId(), product.getName(), product.getImageUrl(),
                    item.getQuantity(), list, unit, taxPercent, lineNet, lineTax));
        }

        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal shipping = (items.isEmpty() || taxable.compareTo(freeShippingThreshold) > 0)
                ? BigDecimal.ZERO.setScale(2) : money(shippingFee);
        BigDecimal total = taxable.add(tax).add(shipping);
        return new PriceQuote(lines, subtotal, discount, taxable, tax, shipping, total, currency);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
