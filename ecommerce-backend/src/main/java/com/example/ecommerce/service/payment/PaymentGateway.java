package com.example.ecommerce.service.payment;

import com.example.ecommerce.model.Order;

/**
 * Integration boundary for online payments. A real adapter (for example Razorpay) must
 * create a provider-side payment order, return PENDING, and only report PAID after a
 * verified, signed provider confirmation. Nothing in the application may mark an online
 * payment as paid without going through this boundary.
 */
public interface PaymentGateway {

    /** True when customers may choose online payment at checkout. */
    boolean enabled();

    /** True for non-production adapters; resulting orders are flagged and excluded from sales. */
    boolean testMode();

    PaymentResult initiate(Order order);

    record PaymentResult(Status status, String reference) {
        public enum Status { PENDING, PAID, FAILED }
    }
}
