package com.example.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Checkout request. Card and UPI details are intentionally not accepted: they must be
 * collected by the payment provider's hosted fields, never by this backend.
 */
public class OrderRequest {
    @NotBlank
    @Size(min = 10, max = 500)
    private String shippingAddress;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phoneNumber;

    /** COD or ONLINE (legacy CARD and UPI are treated as ONLINE). */
    @NotBlank
    @Pattern(regexp = "(?i)^(COD|ONLINE|CARD|UPI)$", message = "Unsupported payment method")
    private String paymentMethod;

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
