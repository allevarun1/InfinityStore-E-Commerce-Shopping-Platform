package com.example.ecommerce;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.WhatsAppOrderService;
import com.example.ecommerce.service.payment.PaymentGateway;
import com.example.ecommerce.service.payment.PaymentGateway.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** WhatsApp message to the owner and the customer when an order is paid; never for unpaid orders. */
class WhatsAppOrderAlertIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired OrderService orderService;
    @Autowired PlatformTransactionManager txManager;
    @MockBean PaymentGateway gateway;
    @MockBean WhatsAppOrderService hook; // what OrderService calls; the real sender is tested below

    @BeforeEach
    void provider() {
        when(gateway.enabled()).thenReturn(true);
        when(gateway.initiate(any())).thenAnswer(i -> new PaymentResult(PaymentResult.Status.PENDING, "order_wa" + N.incrementAndGet()));
        when(gateway.verifyPayment(anyString(), anyString(), eq("good"))).thenReturn(true);
        when(gateway.capture(anyString(), anyString(), anyLong())).thenReturn(true);
    }

    /** The real sender with the WhatsApp call replaced by a recorder; fail=true makes every send fail. */
    private class Recorder extends WhatsAppOrderService {
        final List<String> sent = new CopyOnWriteArrayList<>();
        boolean fail;

        Recorder(String owners, boolean customer) {
            super("token", "12345", owners, true, customer, "new_order_alert", "order_confirmed", "en",
                    "https://shop.test", new long[0], orders, txManager);
        }

        @Override
        protected String send(String to, String template, List<String> values) {
            if (fail) return "not reachable: test";
            sent.add(template + "->" + to + " " + values);
            return null;
        }
    }

    private Order paidOrder() {
        User user = customer();
        addToCart(user, product("1500.00", 5), 1);
        Order o = orderService.placeOrder(user, request("ONLINE"), null);
        return orderService.confirmPayment(user, o.getId(), o.getPaymentReference(), "pay_" + o.getId(), "good");
    }

    @Test
    void unpaidOrdersSendNothingAndPaymentTriggersTheAlert() {
        User user = customer();
        addToCart(user, product("1500.00", 5), 1);
        Order o = orderService.placeOrder(user, request("ONLINE"), null);
        verify(hook, never()).orderPaid(any());

        orderService.confirmPayment(user, o.getId(), o.getPaymentReference(), "pay_x" + o.getId(), "good");
        verify(hook, times(1)).orderPaid(o.getId());
    }

    @Test
    void cancelledBeforePayingSendsNothing() {
        User user = customer();
        addToCart(user, product("1500.00", 5), 1);
        Order o = orderService.placeOrder(user, request("ONLINE"), null);
        orderService.cancelByCustomer(user, o.getId());
        verify(hook, never()).orderPaid(any());
    }

    @Test
    void ownerAndCustomerGetOneMessageEachEvenIfPaymentIsReportedAgain() {
        Order o = paidOrder();
        Recorder wa = new Recorder("+91 98765 43210, 9123456789", true);

        wa.orderPaid(o.getId());
        wa.orderPaid(o.getId()); // Razorpay webhook + checkout + reconcile can all report the same payment

        assertEquals(3, wa.sent.size(), wa.sent.toString());
        assertTrue(wa.sent.stream().anyMatch(s -> s.startsWith("new_order_alert->9876543210")));
        assertTrue(wa.sent.stream().anyMatch(s -> s.startsWith("new_order_alert->9123456789")));
        String customer = wa.sent.stream().filter(s -> s.startsWith("order_confirmed->")).findFirst().orElseThrow();
        assertTrue(customer.contains(o.getTrackingNumber()), customer);
        assertTrue(customer.contains("Rs. "), customer);
        Order after = orders.findById(o.getId()).orElseThrow();
        assertNotNull(after.getWhatsappOwnerAt());
        assertNotNull(after.getWhatsappCustomerAt());
        assertNull(after.getWhatsappError());
        assertEquals("SENT", wa.status(after).owner());
    }

    @Test
    void aFailedSendIsRecordedAndCanBeResent() {
        Order o = paidOrder();
        Recorder wa = new Recorder("9876543210", true);
        wa.fail = true;
        wa.orderPaid(o.getId());

        Order failed = orders.findById(o.getId()).orElseThrow();
        assertNull(failed.getWhatsappOwnerAt(), "nobody got it, so it stays open for Resend");
        assertNotNull(failed.getWhatsappError());
        assertEquals("WAITING", wa.status(failed).owner());
        assertEquals("PAID", failed.getPaymentStatus(), "a WhatsApp problem never touches the order");

        wa.fail = false;
        wa.orderPaid(o.getId());
        assertEquals(2, wa.sent.size());
        assertNull(orders.findById(o.getId()).orElseThrow().getWhatsappError());
    }

    @Test
    void ownerAlsoGetsAnEmailForTheNewPaidOrder() {
        Order o = paidOrder();
        WhatsAppOrderService svc = new WhatsAppOrderService("", "", "", true, true, "a", "b", "en", "https://shop.test",
                new long[0], orders, txManager, mail, "owner@shop.test");
        svc.emailOwner(o.getId());
        verify(mail).send(eq("owner@shop.test"), contains(o.getTrackingNumber()), contains("Deliver to: "));
    }

    @Test
    void messageValuesHaveNoLineBreaksAndBadNumbersAreIgnored() {
        assertEquals(List.of("9876543210"), WhatsAppOrderService.parseNumbers("abc, 12345, +91-98765-43210, 98765 43210"));
        assertEquals("12 MG Road, Hyderabad", WhatsAppOrderService.clean("12 MG Road\nHyderabad"));
    }
}
