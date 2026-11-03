package com.example.ecommerce;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.payment.PaymentGateway;
import com.example.ecommerce.service.payment.PaymentGateway.PaymentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Online payment with Razorpay: the provider is mocked, everything on our side is real. */
class RazorpayFlowIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired OrderService orderService;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PaymentGateway gateway;

    @BeforeEach
    void provider() {
        when(gateway.enabled()).thenReturn(true);
        when(gateway.testMode()).thenReturn(false);
        when(gateway.initiate(any())).thenAnswer(i -> new PaymentResult(PaymentResult.Status.PENDING, "order_" + N.incrementAndGet()));
        when(gateway.checkoutSession(any())).thenAnswer(i -> {
            Order o = i.getArgument(0);
            return new PaymentGateway.CheckoutSession("rzp_live_x", o.getPaymentReference(), PaymentGateway.paise(o.getTotalPrice()), "INR");
        });
        when(gateway.verifyPayment(anyString(), anyString(), eq("good-signature"))).thenReturn(true);
        when(gateway.capture(anyString(), anyString(), anyLong())).thenReturn(true);
        when(gateway.refund(anyString(), anyLong())).thenReturn("rfnd_1");
    }

    private Order placeOnline(User user, Product p) {
        addToCart(user, p, 1);
        return orderService.placeOrder(user, request("ONLINE"), null);
    }

    @Test
    void onlineOrderWaitsForPaymentAndReservesStock() {
        User user = customer();
        Product p = product("1000.00", 5);
        Order order = placeOnline(user, p);
        assertEquals("ONLINE", order.getPaymentMethod());
        assertEquals("PENDING", order.getPaymentStatus());
        assertTrue(order.getPaymentReference().startsWith("order_"));
        assertEquals(4, stockOf(p));

        PaymentGateway.CheckoutSession session = orderService.paymentSession(user, order.getId());
        assertEquals(order.getPaymentReference(), session.providerOrderId());
        assertEquals(PaymentGateway.paise(order.getTotalPrice()), session.amountPaise());
        assertThrows(BusinessException.class, () -> orderService.paymentSession(customer(), order.getId()),
                "someone else cannot pay for or see this order");
    }

    @Test
    void verifiedPaymentMarksTheOrderPaidOnce() {
        User user = customer();
        Order order = placeOnline(user, product("500.00", 5));
        String ref = order.getPaymentReference();

        assertThrows(BusinessException.class, () -> orderService.confirmPayment(user, order.getId(), ref, "pay_1", "forged"));
        assertThrows(BusinessException.class, () -> orderService.confirmPayment(user, order.getId(), "order_other", "pay_1", "good-signature"),
                "a signature for another Razorpay order is not accepted");
        assertEquals("PENDING", orders.findById(order.getId()).orElseThrow().getPaymentStatus());

        Order paid = orderService.confirmPayment(user, order.getId(), ref, "pay_1", "good-signature");
        assertEquals("PAID", paid.getPaymentStatus());
        assertEquals("pay_1", paid.getPaymentId());
        orderService.confirmPayment(user, order.getId(), ref, "pay_1", "good-signature");
        verify(gateway, times(1)).capture("pay_1", ref, PaymentGateway.paise(order.getTotalPrice()));
        assertThrows(BusinessException.class, () -> orderService.cancelByCustomer(user, order.getId()), "paid orders are not self-cancelled");
    }

    @Test
    void amountMismatchIsNotMarkedPaid() {
        User user = customer();
        Order order = placeOnline(user, product("700.00", 5));
        when(gateway.capture(eq("pay_bad"), anyString(), anyLong())).thenReturn(false);
        assertThrows(BusinessException.class,
                () -> orderService.confirmPayment(user, order.getId(), order.getPaymentReference(), "pay_bad", "good-signature"));
        assertEquals("PENDING", orders.findById(order.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void signedWebhookConfirmsAPaymentTheBrowserNeverReported() throws Exception {
        User user = customer();
        Order order = placeOnline(user, product("900.00", 5));
        String body = json.writeValueAsString(Map.of("event", "payment.captured", "payload",
                Map.of("payment", Map.of("entity", Map.of("id", "pay_hook", "order_id", order.getPaymentReference())))));
        when(gateway.verifyWebhook(body, "hook-signature")).thenReturn(true);

        mvc.perform(post("/api/payments/razorpay/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Razorpay-Signature", "wrong")).andExpect(status().isUnauthorized());
        assertEquals("PENDING", orders.findById(order.getId()).orElseThrow().getPaymentStatus());

        for (int i = 0; i < 2; i++) { // Razorpay may deliver the same event more than once
            mvc.perform(post("/api/payments/razorpay/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                    .header("X-Razorpay-Signature", "hook-signature")).andExpect(status().isOk());
        }
        Order paid = orders.findById(order.getId()).orElseThrow();
        assertEquals("PAID", paid.getPaymentStatus());
        assertEquals("pay_hook", paid.getPaymentId());
        verify(gateway, times(1)).capture(eq("pay_hook"), anyString(), anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refundingAPaidOnlineOrderSendsTheMoneyBack() {
        User user = customer();
        Order order = placeOnline(user, product("400.00", 5));
        orderService.confirmPayment(user, order.getId(), order.getPaymentReference(), "pay_r", "good-signature");

        Order refunded = orderService.updatePaymentStatus(order.getId(), "REFUNDED");
        assertEquals("REFUNDED", refunded.getPaymentStatus());
        verify(gateway).refund("pay_r", PaymentGateway.paise(order.getTotalPrice()));
    }

    @Test
    void unpaidOrdersExpireAndReleaseStockButLatePaymentsAreHonoured() {
        User user = customer();
        Product p = product("300.00", 5);
        Order unpaid = placeOnline(user, p);
        Order paidLate = placeOnline(user, p);
        assertEquals(3, stockOf(p));
        for (Order o : new Order[] {unpaid, paidLate}) {
            Order stale = orders.findById(o.getId()).orElseThrow();
            stale.setOrderDate(LocalDateTime.now().minus(OrderService.PAYMENT_WINDOW).minusMinutes(1));
            orders.save(stale);
        }
        when(gateway.findCapturedPayment(unpaid.getPaymentReference())).thenReturn(null);
        when(gateway.findCapturedPayment(paidLate.getPaymentReference())).thenReturn("pay_late");

        orderService.releaseUnpaidOnlineOrders();

        Order expired = orders.findById(unpaid.getId()).orElseThrow();
        assertEquals("CANCELLED", expired.getStatus());
        assertEquals("FAILED", expired.getPaymentStatus());
        assertEquals("PAID", orders.findById(paidLate.getId()).orElseThrow().getPaymentStatus());
        assertEquals(4, stockOf(p), "only the expired order's item goes back on sale");
        assertThrows(BusinessException.class, () -> orderService.paymentSession(user, unpaid.getId()));
    }

    @Test
    void paymentAfterCancellationIsRefundedAutomatically() {
        User user = customer();
        Order order = placeOnline(user, product("250.00", 5));
        orderService.cancelByCustomer(user, order.getId());

        orderService.paymentCaptured(order.getPaymentReference(), "pay_after");

        Order o = orders.findById(order.getId()).orElseThrow();
        assertEquals("REFUNDED", o.getPaymentStatus());
        verify(gateway).refund("pay_after", PaymentGateway.paise(order.getTotalPrice()));
    }

    @Test
    void unpaidOnlineOrderCannotBeConfirmedOrShippedAndIsNotASale() {
        User user = customer();
        Order order = placeOnline(user, product("900.00", 5));
        assertEquals(java.util.List.of("CANCELLED"), orderService.nextStatuses(order), "only cancelling is offered before payment");
        assertThrows(BusinessException.class, () -> orderService.updateOrderStatus(order.getId(), "CONFIRMED"));
        assertThrows(BusinessException.class, () -> orderService.updateOrderStatus(order.getId(), "SHIPPED"));
        assertThrows(BusinessException.class, () -> orderService.updatePaymentStatus(order.getId(), "PAID"),
                "an online payment can never be marked paid by hand");
        assertFalse(com.example.ecommerce.service.InsightsService.counts(orders.findById(order.getId()).orElseThrow()));

        Order paid = orderService.confirmPayment(user, order.getId(), order.getPaymentReference(), "pay_ship", "good-signature");
        assertTrue(com.example.ecommerce.service.InsightsService.counts(paid));
        assertTrue(orderService.nextStatuses(paid).contains("SHIPPED"));
        assertEquals("CONFIRMED", orderService.updateOrderStatus(order.getId(), "CONFIRMED").getStatus());
    }
}
