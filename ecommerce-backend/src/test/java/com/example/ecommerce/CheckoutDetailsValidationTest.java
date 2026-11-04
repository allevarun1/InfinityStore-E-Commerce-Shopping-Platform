package com.example.ecommerce;

import com.example.ecommerce.dto.OrderRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An order can't reach payment without a real mobile number and a delivery address with a PIN code. */
class CheckoutDetailsValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private OrderRequest req(String address, String phone) {
        OrderRequest r = new OrderRequest();
        r.setShippingAddress(address);
        r.setPhoneNumber(phone);
        r.setPaymentMethod("ONLINE");
        return r;
    }

    private Set<String> badFields(OrderRequest r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void completeDetailsPass() {
        assertTrue(badFields(req("Priya\n12 MG Road\nBengaluru, Karnataka - 560001", "9876543210")).isEmpty());
    }

    @Test
    void missingPhoneOrAddressIsRefused() {
        assertEquals(Set.of("shippingAddress", "phoneNumber"), badFields(req("", "")));
        assertEquals(Set.of("shippingAddress", "phoneNumber"), badFields(req(null, null)));
    }

    @Test
    void invalidPhoneIsRefused() {
        String ok = "Priya\n12 MG Road\nBengaluru, Karnataka - 560001";
        assertEquals(Set.of("phoneNumber"), badFields(req(ok, "12345")));
        assertEquals(Set.of("phoneNumber"), badFields(req(ok, "1234567890"))); // Indian mobiles start 6-9
        assertEquals(Set.of("phoneNumber"), badFields(req(ok, "+919876543210")));
    }

    @Test
    void addressWithoutPinIsRefused() {
        assertEquals(Set.of("shippingAddress"), badFields(req("Priya\n12 MG Road\nBengaluru, Karnataka", "9876543210")));
        assertEquals(Set.of("shippingAddress"), badFields(req("Priya\n12 MG Road\nBengaluru - 056000", "9876543210")));
    }
}
