package com.example.ecommerce.dto;

/** What the checkout page may offer. Derived from server configuration, never from the client. */
public record PaymentOptions(boolean cashOnDelivery, boolean online, boolean testMode) {
}
