package com.example.ecommerce.controller;

import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.service.CouponService;
import com.example.ecommerce.service.LeadService;
import com.example.ecommerce.service.payment.PaymentGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Public, non-personal store facts the storefront displays (shipping and payment promises, contact
 * channels, optional analytics ids), plus the newsletter sign-up. Served from the same configuration
 * the pricing engine uses, so the UI cannot drift from what checkout actually charges.
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
            boolean paymentTestMode,
            String whatsappNumber,
            String supportEmail,
            String supportPhone,
            String gaMeasurementId,
            String metaPixelId,
            Offer welcomeOffer,
            BigDecimal defaultGstPercent) {
    }

    /** What a new subscriber gets; shown only while the underlying coupon is usable. */
    public record Offer(String code, String summary) {
    }

    public record SubscribeRequest(@NotBlank @Email @Size(max = 120) String email, @Size(max = 40) String source) {
    }

    public record SubscribeResponse(String message, Offer offer) {
    }

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private LeadService leadService;

    @Autowired
    private CouponService couponService;

    @Value("${app.pricing.currency:INR}")
    private String currency;

    @Value("${app.pricing.free-shipping-threshold:999}")
    private BigDecimal freeShippingThreshold;

    @Value("${app.pricing.default-gst-percent:18}")
    private BigDecimal defaultGstPercent;

    @Value("${app.pricing.shipping-fee:49}")
    private BigDecimal shippingFee;

    /** International format without "+", e.g. 919876543210. Blank hides the WhatsApp buttons. */
    @Value("${app.store.whatsapp:}")
    private String whatsapp;

    @Value("${app.store.support-email:}")
    private String supportEmail;

    @Value("${app.store.support-phone:}")
    private String supportPhone;

    @Value("${app.analytics.ga-measurement-id:}")
    private String gaId;

    @Value("${app.analytics.meta-pixel-id:}")
    private String metaPixelId;

    @Value("${app.growth.welcome-code:WELCOME10}")
    private String welcomeCode;

    @GetMapping("/info")
    public ResponseEntity<StoreInfo> info() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(new StoreInfo(currency, freeShippingThreshold, shippingFee, false, true,
                        paymentGateway.enabled(), paymentGateway.testMode(),
                        digitsOnly(whatsapp), blankToNull(supportEmail), blankToNull(supportPhone),
                        blankToNull(gaId), blankToNull(metaPixelId), welcomeOffer(), defaultGstPercent));
    }

    /** Opt-in sign-up. The response is identical for new and existing subscribers. */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String source = request.source() == null || request.source().isBlank() ? "footer" : request.source().trim();
        leadService.capture(new LeadService.Capture(null, request.email(), null, null, null, source));
        return ResponseEntity.ok(new SubscribeResponse("Thank you for subscribing!", welcomeOffer()));
    }

    private Offer welcomeOffer() {
        if (welcomeCode == null || welcomeCode.isBlank()) {
            return null;
        }
        return couponService.publicOffer(welcomeCode).map(c -> new Offer(c.getCode(), summarize(c))).orElse(null);
    }

    private static String summarize(Coupon c) {
        String off = c.getPercentOff() != null
                ? c.getPercentOff().stripTrailingZeros().toPlainString() + "% off"
                : "Rs. " + c.getFlatOff().stripTrailingZeros().toPlainString() + " off";
        String min = c.getMinSubtotal() != null && c.getMinSubtotal().signum() > 0
                ? " on orders of Rs. " + c.getMinSubtotal().setScale(0, java.math.RoundingMode.CEILING) + " or more" : "";
        return off + min + (c.isFirstOrderOnly() ? " (first order only)" : "");
    }

    private static String digitsOnly(String v) {
        String d = v == null ? "" : v.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
