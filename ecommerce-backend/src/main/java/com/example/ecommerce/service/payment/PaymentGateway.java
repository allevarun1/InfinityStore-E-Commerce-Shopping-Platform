package com.example.ecommerce.service.payment;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.service.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Integration boundary for online payments. A real adapter (for example Razorpay) must
 * create a provider-side payment order, return PENDING, and only report PAID after a
 * verified, signed provider confirmation. Nothing in the application may mark an online
 * payment as paid without going through this boundary.
 */
public interface PaymentGateway {

    /** Rupees to the paise providers work in. */
    static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** True when customers may choose online payment at checkout. */
    boolean enabled();

    /** True for non-production adapters; resulting orders are flagged and excluded from sales. */
    boolean testMode();

    PaymentResult initiate(Order order);

    record PaymentResult(Status status, String reference) {
        public enum Status { PENDING, PAID, FAILED }
    }

    /** What the storefront needs to open the provider's payment window for an order. */
    record CheckoutSession(String keyId, String providerOrderId, long amountPaise, String currency) {
    }

    default CheckoutSession checkoutSession(Order order) {
        throw new BusinessException("Online payment is not available");
    }

    /** True when the signature proves the provider approved this payment for this provider order. */
    default boolean verifyPayment(String providerOrderId, String paymentId, String signature) {
        return false;
    }

    /** True when a webhook body really came from the provider. */
    default boolean verifyWebhook(String body, String signature) {
        return false;
    }

    /**
     * Makes sure the money for this payment is captured (not just authorised) and that the amount is right.
     * Returns false when the payment is not usable for the order.
     */
    default boolean capture(String paymentId, String providerOrderId, long amountPaise) {
        return false;
    }

    /** The id of a captured payment for this provider order, if the customer paid but we never heard back. */
    default String findCapturedPayment(String providerOrderId) {
        return null;
    }

    /** Sends the full amount back to the customer; returns the provider's refund id. */
    default String refund(String paymentId, long amountPaise) {
        throw new BusinessException("Refunds are not available for this payment");
    }
}
