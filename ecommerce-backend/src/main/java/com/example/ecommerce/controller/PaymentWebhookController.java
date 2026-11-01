package com.example.ecommerce.controller;

import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.payment.PaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Razorpay calls this when a payment succeeds, so an order is confirmed even if the customer closed the
 * page before our site heard back. Public, but every call must carry a valid X-Razorpay-Signature.
 */
@RestController
@RequestMapping("/api/payments/razorpay")
public class PaymentWebhookController {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);
    private static final Set<String> PAID_EVENTS = Set.of("payment.authorized", "payment.captured", "order.paid");

    @Autowired private PaymentGateway paymentGateway;
    @Autowired private OrderService orderService;
    @Autowired private ObjectMapper json;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody(required = false) String body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) throws Exception {
        if (!paymentGateway.verifyWebhook(body, signature)) {
            log.warn("Rejected a Razorpay webhook with a missing or wrong signature");
            return ResponseEntity.status(401).build();
        }
        JsonNode event = json.readTree(body);
        if (PAID_EVENTS.contains(event.path("event").asText())) {
            JsonNode payment = event.path("payload").path("payment").path("entity");
            String providerOrderId = payment.path("order_id").asText("");
            String paymentId = payment.path("id").asText("");
            if (!providerOrderId.isEmpty() && !paymentId.isEmpty()) {
                // Errors propagate as non-2xx so Razorpay retries later.
                orderService.paymentCaptured(providerOrderId, paymentId);
            }
        }
        return ResponseEntity.ok().build();
    }
}
