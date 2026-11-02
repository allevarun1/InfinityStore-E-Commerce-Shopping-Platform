package com.example.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The shop's delivery and payment rules in one place, so checkout, prices, messages and the stylist all agree.
 * COD_ENABLED=false turns off cash on delivery; FREE_SHIPPING_THRESHOLD=0 (or empty) means delivery is always charged.
 */
@Component
public class StorePolicy {
    private final boolean codEnabled;
    private final BigDecimal freeShippingThreshold;
    private final BigDecimal shippingFee;

    public StorePolicy(@Value("${app.payment.cod-enabled:true}") boolean codEnabled,
                       @Value("${app.pricing.free-shipping-threshold:999}") String freeShippingThreshold,
                       @Value("${app.pricing.shipping-fee:49}") BigDecimal shippingFee) {
        this.codEnabled = codEnabled;
        BigDecimal t = freeShippingThreshold == null || freeShippingThreshold.isBlank() ? null : new BigDecimal(freeShippingThreshold.trim());
        this.freeShippingThreshold = t == null || t.signum() <= 0 ? null : t;
        this.shippingFee = shippingFee == null ? BigDecimal.ZERO : shippingFee;
    }

    public boolean codEnabled() {
        return codEnabled;
    }

    /** Orders above this ship free; null when the shop has no free shipping. */
    public BigDecimal freeShippingThreshold() {
        return freeShippingThreshold;
    }

    public boolean hasFreeShipping() {
        return freeShippingThreshold != null;
    }

    public BigDecimal shippingFee() {
        return shippingFee;
    }
}
