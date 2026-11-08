package com.example.ecommerce;

import com.example.ecommerce.model.ERole;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.security.RateLimitFilter;
import com.example.ecommerce.security.RateLimiter;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.payment.PaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Online checkout hands back the payment-window details at once, and cannot be used to hoard stock or hammer Razorpay. */
class PaymentSpeedAndSafetyIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orderService;
    @MockBean PaymentGateway gateway;

    private final AtomicInteger seq = new AtomicInteger();

    private void razorpayLikeGateway() {
        when(gateway.enabled()).thenReturn(true);
        when(gateway.testMode()).thenReturn(false);
        when(gateway.initiate(any())).thenAnswer(i ->
                new PaymentGateway.PaymentResult(PaymentGateway.PaymentResult.Status.PENDING, "order_T" + seq.incrementAndGet()));
        when(gateway.checkoutSession(any())).thenAnswer(i -> {
            var o = (com.example.ecommerce.model.Order) i.getArgument(0);
            return new PaymentGateway.CheckoutSession("rzp_test_key", o.getPaymentReference(), 12300, "INR");
        });
    }

    private User customerWithPassword(String name) {
        User u = new User(name, name + "@test.local", encoder.encode("password-" + name), "Pay", "Test");
        u.setRoles(Set.of(roles.findByName(ERole.ROLE_CUSTOMER).orElseThrow()));
        return users.save(u);
    }

    private String signIn(String name) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", name, "password", "password-" + name)))).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void checkoutReturnsThePaymentWindowDetailsSoTheBrowserNeedsNoSecondCall() throws Exception {
        razorpayLikeGateway();
        User user = customerWithPassword("fastpay1");
        Product p = product("400.00", 5);
        addToCart(user, p, 1);

        MvcResult r = mvc.perform(post("/api/orders/checkout").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + signIn("fastpay1"))
                .content(json.writeValueAsString(Map.of(
                        "shippingAddress", "12 Test Street, Bengaluru, Karnataka - 560001",
                        "phoneNumber", "9876543210", "paymentMethod", "ONLINE")))).andReturn();

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertEquals("PENDING", body.get("paymentStatus").asText());
        JsonNode payment = body.get("payment");
        assertNotNull(payment, "an online order must carry the payment-window details");
        assertEquals("rzp_test_key", payment.get("keyId").asText());
        assertTrue(payment.get("providerOrderId").asText().startsWith("order_T"));
        assertEquals(12300, payment.get("amountPaise").asLong());
    }

    @Test
    void plainOrderViewCarriesNoPaymentDetails() throws Exception {
        razorpayLikeGateway();
        User user = customerWithPassword("fastpay2");
        Product p = product("400.00", 5);
        addToCart(user, p, 1);
        var order = orderService.placeOrder(user, request("ONLINE"), null);
        var view = com.example.ecommerce.dto.OrderResponse.forCustomer(order, java.util.List.of());
        assertNull(view.payment(), "the plain order view must not include payment details");
    }

    @Test
    void oneAccountCannotHoldStockWithMoreThanThreeUnpaidOnlineOrders() {
        razorpayLikeGateway();
        User user = customerWithPassword("hoarder1");
        for (int i = 0; i < 3; i++) {
            addToCart(user, product("400.00", 5), 1);
            assertEquals("PENDING", orderService.placeOrder(user, request("ONLINE"), null).getPaymentStatus());
        }
        addToCart(user, product("400.00", 5), 1);
        BusinessException refused = assertThrows(BusinessException.class,
                () -> orderService.placeOrder(user, request("ONLINE"), null));
        assertTrue(refused.getMessage().contains("unpaid orders"), refused.getMessage());
    }

    @Test
    void checkoutAndPaymentEndpointsAreRateLimitedPerIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimiter(), 1);
        int[] limits = {20, 30, 30};
        String[] paths = {"/api/orders/checkout", "/api/orders/42/pay", "/api/orders/42/pay/verify"};
        for (int k = 0; k < paths.length; k++) {
            int refused = 0;
            for (int i = 0; i < limits[k] + 3; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", paths[k]);
                req.setServletPath(paths[k]);
                req.setRemoteAddr("203.0.113." + (k + 1));
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilter(req, res, new MockFilterChain());
                if (res.getStatus() == 429) refused++;
            }
            assertEquals(3, refused, paths[k] + " should be refused after " + limits[k] + " tries in the window");
        }
        // Someone else on a different address is unaffected.
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/orders/checkout");
        other.setServletPath("/api/orders/checkout");
        other.setRemoteAddr("198.51.100.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(other, res, new MockFilterChain());
        assertNotEquals(429, res.getStatus());
    }
}
