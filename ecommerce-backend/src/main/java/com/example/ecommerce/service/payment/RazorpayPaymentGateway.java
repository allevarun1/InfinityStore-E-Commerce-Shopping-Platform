package com.example.ecommerce.service.payment;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.service.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Razorpay (UPI, cards, netbanking, wallets). The order is created on Razorpay when the customer places it,
 * the storefront opens Razorpay Checkout, and the order is marked paid only after the HMAC signature Razorpay
 * returns (or a signed webhook) checks out and the payment is captured for the exact amount.
 * Keys starting rzp_test_ run in Razorpay test mode: those orders are flagged and kept out of sales.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);
    private static final String API = "https://api.razorpay.com/v1";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestClient client;

    public RazorpayPaymentGateway(
            @Value("${app.payment.razorpay.key-id:}") String keyId,
            @Value("${app.payment.razorpay.key-secret:}") String keySecret,
            @Value("${app.payment.razorpay.webhook-secret:}") String webhookSecret) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(API).requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(this.keyId, this.keySecret)).build();
        if (!enabled()) {
            log.warn("PAYMENT_PROVIDER=razorpay but RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are missing; online payment stays off");
        }
    }

    @Override
    public boolean enabled() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    @Override
    public boolean testMode() {
        return keyId.startsWith("rzp_test_");
    }

    @Override
    public PaymentResult initiate(Order order) {
        Map<String, Object> body = Map.of(
                "amount", PaymentGateway.paise(order.getTotalPrice()),
                "currency", order.getCurrency() == null ? "INR" : order.getCurrency(),
                "receipt", order.getTrackingNumber(),
                "notes", Map.of("order", order.getTrackingNumber()));
        try {
            JsonNode created = client.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            return new PaymentResult(PaymentResult.Status.PENDING, created.path("id").asText());
        } catch (RestClientException e) {
            log.error("Razorpay order creation failed for {}: {}", order.getTrackingNumber(), e.getMessage());
            throw new BusinessException("Online payment is not reachable right now. Please try again or choose Cash on delivery.");
        }
    }

    @Override
    public CheckoutSession checkoutSession(Order order) {
        return new CheckoutSession(keyId, order.getPaymentReference(), PaymentGateway.paise(order.getTotalPrice()),
                order.getCurrency() == null ? "INR" : order.getCurrency());
    }

    @Override
    public boolean verifyPayment(String providerOrderId, String paymentId, String signature) {
        return providerOrderId != null && paymentId != null
                && matches(hmac(keySecret, providerOrderId + "|" + paymentId), signature);
    }

    @Override
    public boolean verifyWebhook(String body, String signature) {
        return !webhookSecret.isEmpty() && body != null && matches(hmac(webhookSecret, body), signature);
    }

    @Override
    public boolean capture(String paymentId, String providerOrderId, long amountPaise) {
        try {
            JsonNode payment = client.get().uri("/payments/{id}", paymentId).retrieve().body(JsonNode.class);
            if (!providerOrderId.equals(payment.path("order_id").asText()) || payment.path("amount").asLong() != amountPaise) {
                log.error("Razorpay payment {} does not match order {} / amount {}", paymentId, providerOrderId, amountPaise);
                return false;
            }
            String status = payment.path("status").asText();
            if ("captured".equals(status)) {
                return true;
            }
            if ("authorized".equals(status)) {
                JsonNode captured = client.post().uri("/payments/{id}/capture", paymentId).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("amount", amountPaise, "currency", payment.path("currency").asText("INR")))
                        .retrieve().body(JsonNode.class);
                return "captured".equals(captured.path("status").asText());
            }
            return false;
        } catch (RestClientException e) {
            log.error("Razorpay capture check failed for {}: {}", paymentId, e.getMessage());
            throw new BusinessException("We could not confirm the payment with Razorpay yet. If money was taken it will be confirmed automatically.");
        }
    }

    @Override
    public String findCapturedPayment(String providerOrderId) {
        try {
            JsonNode list = client.get().uri("/orders/{id}/payments", providerOrderId).retrieve().body(JsonNode.class);
            for (JsonNode p : list.path("items")) {
                if ("captured".equals(p.path("status").asText())) {
                    return p.path("id").asText();
                }
            }
            return null;
        } catch (RestClientException e) {
            log.warn("Razorpay payment lookup failed for {}: {}", providerOrderId, e.getMessage());
            throw new BusinessException("Razorpay is not reachable");
        }
    }

    @Override
    public String refund(String paymentId, long amountPaise) {
        try {
            JsonNode refund = client.post().uri("/payments/{id}/refund", paymentId).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountPaise)).retrieve().body(JsonNode.class);
            return refund.path("id").asText();
        } catch (RestClientException e) {
            log.error("Razorpay refund failed for {}: {}", paymentId, e.getMessage());
            throw new BusinessException("Razorpay could not start the refund. Please try again or refund from the Razorpay dashboard.");
        }
    }

    static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matches(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                given.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
