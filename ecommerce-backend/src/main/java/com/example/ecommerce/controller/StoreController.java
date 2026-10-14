package com.example.ecommerce.controller;

import com.example.ecommerce.service.payment.PaymentGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Public, non-personal store facts the storefront displays (shipping and payment promises).
 * Served from the same configuration the pricing engine uses, so the UI cannot drift from
 * what checkout actually charges.
 */
@RestController
@RequestMapping("/api/store")
public class StoreController {

    public record StoreInfo(
            String currency,
            BigDecimal freeShippingThreshold,
            BigDecimal shippingFee,
            boolean pricesIncludeTax,
            boolean cashOnDelivery,
            boolean onlinePayment,
            boolean paymentTestMode) {
    }

    @Autowired
    private PaymentGateway paymentGateway;

    @Value("${app.pricing.currency:INR}")
    private String currency;

    @Value("${app.pricing.free-shipping-threshold:999}")
    private BigDecimal freeShippingThreshold;

    @Value("${app.pricing.shipping-fee:49}")
    private BigDecimal shippingFee;

    @GetMapping("/info")
    public ResponseEntity<StoreInfo> info() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(new StoreInfo(currency, freeShippingThreshold, shippingFee, false, true,
                        paymentGateway.enabled(), paymentGateway.testMode()));
    }
}
