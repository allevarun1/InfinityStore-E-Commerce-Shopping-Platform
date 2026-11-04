package com.example.ecommerce.controller;

import com.example.ecommerce.model.Coupon;
import com.example.ecommerce.security.FirebasePhoneVerifier;
import com.example.ecommerce.security.GoogleIdTokenVerifier;
import com.example.ecommerce.service.TruecallerLoginService;
import com.example.ecommerce.service.WhatsAppOtpService;
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
            BigDecimal defaultGstPercent,
            FirebasePhoneVerifier.WebConfig phoneLogin,
            LoginOptions login,
            boolean emailConfirmations) {
    }

    /** Which quick sign-in buttons the storefront shows; all values are public. */
    public record LoginOptions(String googleClientId, TruecallerLoginService.WebConfig truecaller, boolean whatsappOtp) {
    }

    /** What a new subscriber gets; shown only while the underlying coupon is usable. */
    /** productIds: the sarees the code works on; empty = all. */
    public record Offer(String code, String summary, java.util.List<Long> productIds) {
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

    @Autowired
    private FirebasePhoneVerifier phoneVerifier;

    @Autowired
    private GoogleIdTokenVerifier googleVerifier;

    @Autowired
    private TruecallerLoginService truecaller;

    @Autowired
    private WhatsAppOtpService whatsappOtp;

    @Value("${app.pricing.currency:INR}")
    private String currency;

    @Autowired
    private com.example.ecommerce.service.StorePolicy policy;

    @Value("${app.pricing.default-gst-percent:0}")
    private BigDecimal defaultGstPercent;



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

    @Autowired
    private com.example.ecommerce.service.CatalogCache cache;

    @Autowired
    private com.example.ecommerce.service.MailService mailService;

    /** Read on every page; kept in memory and cleared when a coupon (the welcome offer) changes. */
    @GetMapping("/info")
    public ResponseEntity<StoreInfo> info() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(cache.get("store-info", () -> new StoreInfo(currency, policy.freeShippingThreshold(), policy.shippingFee(), false, policy.codEnabled(),
                        paymentGateway.enabled(), paymentGateway.testMode(),
                        digitsOnly(whatsapp), blankToNull(supportEmail), blankToNull(supportPhone),
                        blankToNull(gaId), blankToNull(metaPixelId), welcomeOffer(), defaultGstPercent,
                        phoneVerifier.webConfig(),
                        new LoginOptions(googleVerifier.clientId(), truecaller.webConfig(), whatsappOtp.enabled()),
                        mailService.configured())));
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
        return couponService.publicOffer(welcomeCode).map(c -> new Offer(c.getCode(), summarize(c), c.getProductIds().stream().sorted().toList())).orElse(null);
    }

    private static String summarize(Coupon c) {
        String off = c.getPercentOff() != null
                ? c.getPercentOff().stripTrailingZeros().toPlainString() + "% off"
                : "Rs. " + c.getFlatOff().stripTrailingZeros().toPlainString() + " off";
        String min = c.getMinSubtotal() != null && c.getMinSubtotal().signum() > 0
                ? " on orders of Rs. " + c.getMinSubtotal().setScale(0, java.math.RoundingMode.CEILING) + " or more" : "";
        String on = c.getProductIds().isEmpty() ? "" : " on selected sarees";
        return off + on + min + (c.isFirstOrderOnly() ? " (first order only)" : "");
    }

    private static String digitsOnly(String v) {
        String d = v == null ? "" : v.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
