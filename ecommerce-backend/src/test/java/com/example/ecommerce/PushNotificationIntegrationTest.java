package com.example.ecommerce;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.PushDevice;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.AppSettingRepository;
import com.example.ecommerce.repository.PushDeviceRepository;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.push.PushService;
import com.example.ecommerce.service.push.WebPush;
import com.example.ecommerce.service.payment.PaymentGateway;
import com.example.ecommerce.service.payment.PaymentGateway.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Phone notifications: only the owner's devices, only for paid orders, only real push services. */
class PushNotificationIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired OrderService orderService;
    @Autowired PushDeviceRepository devices;
    @Autowired AppSettingRepository settings;
    @Autowired PlatformTransactionManager txManager;
    @MockBean PaymentGateway gateway;
    @MockBean PushService hook; // what OrderService calls; a real PushService with a fake network is built below

    @BeforeEach
    void provider() {
        when(gateway.enabled()).thenReturn(true);
        when(gateway.initiate(any())).thenAnswer(i -> new PaymentResult(PaymentResult.Status.PENDING, "order_push" + N.incrementAndGet()));
        when(gateway.verifyPayment(anyString(), anyString(), eq("good"))).thenReturn(true);
        when(gateway.capture(anyString(), anyString(), anyLong())).thenReturn(true);
    }

    /** Real PushService; the push service's reply is chosen by the test. */
    class FakeNet extends PushService {
        final List<String> to = new CopyOnWriteArrayList<>();
        int reply = 201;

        FakeNet() {
            super(devices, settings, orders, txManager, "", "", "mailto:t@t.test");
        }

        @Override
        protected int deliver(HttpRequest req) {
            assertTrue(req.headers().firstValue("Authorization").orElse("").startsWith("vapid t="));
            assertEquals("aes128gcm", req.headers().firstValue("Content-Encoding").orElse(""));
            to.add(req.uri().toString());
            return reply;
        }
    }

    private PushService.Subscription browser(String id) {
        var kp = com.example.ecommerce.service.push.WebPushAccess.keyPair();
        return new PushService.Subscription("https://fcm.googleapis.com/fcm/send/" + id, kp, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
    }

    private Order paid(User user) {
        addToCart(user, product("2000.00", 5), 1);
        Order o = orderService.placeOrder(user, request("ONLINE"), null);
        return orderService.confirmPayment(user, o.getId(), o.getPaymentReference(), "pay_p" + o.getId(), "good");
    }

    @Test
    void paymentTriggersTheNotificationButAnUnpaidOrderDoesNot() {
        User user = customer();
        addToCart(user, product("2000.00", 5), 1);
        Order o = orderService.placeOrder(user, request("ONLINE"), null);
        verify(hook, never()).orderPaid(any());
        orderService.confirmPayment(user, o.getId(), o.getPaymentReference(), "pay_q" + o.getId(), "good");
        verify(hook, times(1)).orderPaid(o.getId());
    }

    @Test
    void onlyTheOwnersPhonesAreNotifiedAndOnlyOnce() {
        FakeNet push = new FakeNet();
        User owner = customer();
        User buyer = customer();
        String tag = "t" + N.incrementAndGet();
        push.register(owner.getId(), true, browser(tag + "owner"), "Mozilla/5.0 (Linux; Android 14) Chrome/130");
        push.register(buyer.getId(), false, browser(tag + "buyer"), "iPhone Safari"); // e.g. an old customer subscription
        Order o = paid(buyer);

        push.orderPaid(o.getId());
        push.orderPaid(o.getId()); // the same payment reported again

        assertEquals(1, push.to.stream().filter(u -> u.endsWith(tag + "owner")).count(), "the owner hears once per order");
        assertFalse(push.to.stream().anyMatch(u -> u.endsWith(tag + "buyer")), "customers are never notified");
    }

    @Test
    void aPhoneThatTurnedNotificationsOffIsForgotten() {
        FakeNet push = new FakeNet();
        User buyer = customer();
        String id = "gone" + N.incrementAndGet();
        PushDevice d = push.register(buyer.getId(), false, browser(id), "x");
        push.reply = 410;
        push.test(buyer.getId(), "https://fcm.googleapis.com/fcm/send/" + id);
        assertTrue(devices.findById(d.getId()).isEmpty());
    }

    @Test
    void onlyRealPushServicesAndOnlyYourOwnDevice() {
        FakeNet push = new FakeNet();
        User a = customer();
        var keys = browser("x");
        assertThrows(BusinessException.class, () -> push.register(a.getId(), false,
                new PushService.Subscription("https://evil.example.com/collect", keys.p256dh(), keys.auth()), "x"));
        assertThrows(BusinessException.class, () -> push.register(a.getId(), false,
                new PushService.Subscription("http://fcm.googleapis.com/fcm/send/x", keys.p256dh(), keys.auth()), "x"));

        String id = "mine" + N.incrementAndGet();
        push.register(a.getId(), false, browser(id), "x");
        User b = customer();
        assertThrows(BusinessException.class, () -> push.test(b.getId(), "https://fcm.googleapis.com/fcm/send/" + id));
        push.unregister(b.getId(), "https://fcm.googleapis.com/fcm/send/" + id);
        assertTrue(devices.findByUserId(a.getId()).size() == 1, "someone else cannot remove my device");
    }

    @Test
    void theSigningKeyIsCreatedOnceAndKept() {
        String k1 = new FakeNet().vapid().publicKey();
        String k2 = new FakeNet().vapid().publicKey();
        assertEquals(k1, k2);
        assertEquals(65, java.util.Base64.getUrlDecoder().decode(k1).length);
    }
}
