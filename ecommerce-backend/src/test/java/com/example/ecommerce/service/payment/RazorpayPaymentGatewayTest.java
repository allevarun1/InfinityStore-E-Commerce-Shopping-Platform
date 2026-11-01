package com.example.ecommerce.service.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayPaymentGatewayTest {
    private final RazorpayPaymentGateway gateway = new RazorpayPaymentGateway("rzp_test_abc", "key-secret", "hook-secret");

    @Test
    void checkoutSignatureIsHmacOfOrderAndPaymentWithTheKeySecret() {
        String good = RazorpayPaymentGateway.hmac("key-secret", "order_1|pay_1");
        assertTrue(gateway.verifyPayment("order_1", "pay_1", good));
        assertTrue(gateway.verifyPayment("order_1", "pay_1", good.toUpperCase()));
        assertFalse(gateway.verifyPayment("order_2", "pay_1", good), "signature is bound to the order");
        assertFalse(gateway.verifyPayment("order_1", "pay_2", good), "signature is bound to the payment");
        assertFalse(gateway.verifyPayment("order_1", "pay_1", RazorpayPaymentGateway.hmac("hook-secret", "order_1|pay_1")));
        assertFalse(gateway.verifyPayment("order_1", "pay_1", null));
    }

    @Test
    void webhookSignatureUsesTheWebhookSecretOverTheRawBody() {
        String body = "{\"event\":\"payment.captured\"}";
        assertTrue(gateway.verifyWebhook(body, RazorpayPaymentGateway.hmac("hook-secret", body)));
        assertFalse(gateway.verifyWebhook(body + " ", RazorpayPaymentGateway.hmac("hook-secret", body)));
        assertFalse(gateway.verifyWebhook(body, RazorpayPaymentGateway.hmac("key-secret", body)));
        assertFalse(new RazorpayPaymentGateway("rzp_test_abc", "key-secret", "").verifyWebhook(body, ""),
                "no webhook secret configured means no webhook is trusted");
    }

    @Test
    void modeAndAvailabilityFollowTheKeys() {
        assertTrue(gateway.enabled());
        assertTrue(gateway.testMode());
        assertFalse(new RazorpayPaymentGateway("rzp_live_abc", "s", "").testMode());
        assertFalse(new RazorpayPaymentGateway("rzp_live_abc", "", "").enabled());
    }

    @Test
    void rupeesBecomePaise() {
        assertEquals(129900, PaymentGateway.paise(new BigDecimal("1299.00")));
        assertEquals(100, PaymentGateway.paise(new BigDecimal("0.995")));
    }
}
