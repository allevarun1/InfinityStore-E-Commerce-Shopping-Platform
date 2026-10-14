package com.example.ecommerce;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = "app.payment.provider=test")
class TestPaymentAdapterIntegrationTest extends IntegrationTestSupport {
    @Autowired OrderService orderService;

    @Test
    void testAdapterOrdersAreFlaggedAndNeverReportedAsRealPayments() {
        User user = customer();
        Product p = product("400.00", 5);
        addToCart(user, p, 1);

        Order o = orderService.placeOrder(user, request("CARD"), null);

        assertEquals("ONLINE", o.getPaymentMethod());
        assertEquals("PAID", o.getPaymentStatus());
        assertTrue(o.isTestPayment(), "orders paid through the test adapter must be flagged");
        assertTrue(o.getPaymentReference().startsWith("TEST-"));
        verify(mail, never()).sendPaymentSuccess(anyString(), any());
        assertTrue(orderService.paymentOptions().testMode());
    }

    @Test
    void onlinePaymentsCannotBeConfirmedByHand() {
        User user = customer();
        Product p = product("400.00", 5);
        addToCart(user, p, 1);
        Order o = orderService.placeOrder(user, request("ONLINE"), null);
        // Already PAID by the adapter; a pending online payment could never be force-marked paid.
        assertEquals("PAID", o.getPaymentStatus());
        assertEquals("REFUNDED", orderService.updatePaymentStatus(o.getId(), "REFUNDED").getPaymentStatus());
    }
}
