package com.example.ecommerce.service.push;

import com.example.ecommerce.model.AppSetting;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.model.PushDevice;
import com.example.ecommerce.repository.AppSettingRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.PushDeviceRepository;
import com.example.ecommerce.service.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phone notifications like an app's, for the shop owner only: "New paid order ₹4,499" the moment a customer pays,
 * so the order can be packed and shipped. Customers are never notified. Uses the browsers' own Web Push, so there
 * is no extra account or cost. The signing key is created once and kept in the database (or given as VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY).
 */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    /** Only real browser push services, so a crafted "subscription" can never make the server call anything else. */
    private static final List<String> PUSH_HOSTS = List.of("fcm.googleapis.com", "android.googleapis.com",
            "push.services.mozilla.com", "notify.windows.com", "push.apple.com");

    private final PushDeviceRepository devices;
    private final AppSettingRepository settings;
    private final OrderRepository orders;
    private final TransactionTemplate tx;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String envPublic;
    private final String envPrivate;
    private final String subject;
    private volatile WebPush.Vapid vapid;

    public PushService(PushDeviceRepository devices, AppSettingRepository settings, OrderRepository orders,
                       PlatformTransactionManager txManager,
                       @Value("${app.push.vapid-public-key:}") String envPublic,
                       @Value("${app.push.vapid-private-key:}") String envPrivate,
                       @Value("${app.push.subject:mailto:orders@rekhashoppingmall.in}") String subject) {
        this.devices = devices;
        this.settings = settings;
        this.orders = orders;
        this.tx = new TransactionTemplate(txManager);
        this.envPublic = envPublic == null ? "" : envPublic.trim();
        this.envPrivate = envPrivate == null ? "" : envPrivate.trim();
        this.subject = subject == null || subject.isBlank() ? "mailto:orders@rekhashoppingmall.in" : subject.trim();
    }

    // ---------------------------------------------------------------- keys and devices

    /** The public key browsers need to subscribe. Created on first use and then kept. */
    public synchronized WebPush.Vapid vapid() {
        if (vapid != null) return vapid;
        if (!envPublic.isEmpty() && !envPrivate.isEmpty()) {
            return vapid = new WebPush.Vapid(envPublic, envPrivate);
        }
        WebPush.Vapid v = tx.execute(s -> {
            var pub = settings.findById("push.vapid.public");
            var priv = settings.findById("push.vapid.private");
            if (pub.isPresent() && priv.isPresent()) return new WebPush.Vapid(pub.get().getValue(), priv.get().getValue());
            WebPush.Vapid fresh = WebPush.Vapid.generate();
            settings.save(new AppSetting("push.vapid.public", fresh.publicKey()));
            settings.save(new AppSetting("push.vapid.private", fresh.privateKey()));
            log.info("Created the shop's notification signing key");
            return fresh;
        });
        return vapid = v;
    }

    public record Subscription(String endpoint, String p256dh, String auth) {
    }

    /** Saves (or moves to this user) an admin's browser subscription for the new-order alerts. */
    public PushDevice register(Long userId, boolean admin, Subscription s, String userAgent) {
        String endpoint = checkEndpoint(s.endpoint());
        if (s.p256dh() == null || s.auth() == null || s.p256dh().length() > 120 || s.auth().length() > 40) {
            throw new BusinessException("This browser sent an incomplete notification key. Please try again.");
        }
        return tx.execute(st -> {
            PushDevice d = devices.findByEndpointHash(hash(endpoint)).orElseGet(PushDevice::new);
            d.setEndpoint(endpoint);
            d.setEndpointHash(hash(endpoint));
            d.setP256dh(s.p256dh().trim());
            d.setAuth(s.auth().trim());
            d.setUserId(userId);
            d.setAdmin(admin);
            d.setLabel(label(userAgent));
            return devices.save(d);
        });
    }

    /** Removes this browser's subscription, only if it belongs to the user. */
    public void unregister(Long userId, String endpoint) {
        tx.executeWithoutResult(st -> devices.findByEndpointHash(hash(endpoint == null ? "" : endpoint.trim()))
                .filter(d -> d.getUserId().equals(userId)).ifPresent(devices::delete));
    }

    public long adminDeviceCount() {
        return devices.countByAdminTrue();
    }

    /** Sends "Notifications are on" to this browser; true when the push service accepted it. */
    public boolean test(Long userId, String endpoint) {
        PushDevice d = devices.findByEndpointHash(hash(endpoint == null ? "" : endpoint.trim()))
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException("Turn notifications on first"));
        return send(d, note("Notifications are on", "You will get an alert like this for every new paid order.", "/admin?tab=orders", "test"));
    }

    // ---------------------------------------------------------------- order events

    /** New paid order: an alert on every admin device, once per order. Nothing is sent to customers. */
    @Async
    public void orderPaid(Long orderId) {
        if (orders.claimPushPaid(orderId, LocalDateTime.now()) != 1) return;
        Facts f = tx.execute(s -> orders.findById(orderId).map(PushService::facts).orElse(null));
        if (f == null) return;
        Map<String, Object> owner = note((f.test ? "TEST · " : "") + "New paid order " + f.amount,
                f.name + " · " + f.items + (f.city.isEmpty() ? "" : " · " + f.city) + " · pack and ship", "/admin?tab=orders", "order-" + orderId);
        owner.put("requireInteraction", true);
        int sent = 0;
        for (PushDevice d : devices.findByAdminTrue()) if (send(d, owner)) sent++;
        log.info("Order {} paid: phone alert sent to {} admin device(s)", orderId, sent);
    }

    record Facts(String number, String name, String items, String amount, String city, boolean test) {
    }

    static Facts facts(Order o) {
        var u = o.getUser();
        String name = u == null ? "Customer" : ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
        if (name.isEmpty()) name = u == null || u.getUsername() == null ? "Customer" : u.getUsername();
        String items = o.getOrderItems().stream().map(PushService::itemName).collect(Collectors.joining(", "));
        if (items.length() > 80) items = items.substring(0, 79) + "…";
        NumberFormat inr = NumberFormat.getIntegerInstance(new Locale("en", "IN"));
        String amount = "₹" + inr.format(o.getTotalPrice().setScale(0, java.math.RoundingMode.HALF_UP));
        return new Facts(o.getTrackingNumber(), name, items, amount, city(o.getShippingAddress()), o.isTestPayment());
    }

    private static String itemName(OrderItem i) {
        String n = i.getProductName() != null ? i.getProductName() : i.getProduct() != null ? i.getProduct().getName() : "Saree";
        return i.getQuantity() > 1 ? n + " ×" + i.getQuantity() : n;
    }

    /** "12 MG Road, Hyderabad, Telangana 500001" → "Hyderabad". */
    static String city(String address) {
        if (address == null) return "";
        String[] p = address.split(",");
        return p.length >= 3 ? p[p.length - 2].trim() : "";
    }

    static Map<String, Object> note(String title, String body, String url, String tag) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("body", body);
        m.put("url", url);
        m.put("tag", tag);
        return m;
    }

    // ---------------------------------------------------------------- sending

    /** Encrypts and posts one notification. A device that has gone away (404/410) is removed. */
    boolean send(PushDevice d, Map<String, Object> notification) {
        try {
            byte[] body = WebPush.encrypt(json.writeValueAsBytes(notification), d.getP256dh(), d.getAuth());
            HttpRequest req = HttpRequest.newBuilder(URI.create(d.getEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", WebPush.authorization(d.getEndpoint(), vapid(), subject, Instant.now().getEpochSecond()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "high")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            int code = deliver(req);
            if (code >= 200 && code < 300) {
                tx.executeWithoutResult(s -> devices.findById(d.getId()).ifPresent(x -> { x.setLastOkAt(LocalDateTime.now()); devices.save(x); }));
                return true;
            }
            if (code == 404 || code == 410) {
                tx.executeWithoutResult(s -> devices.findById(d.getId()).ifPresent(devices::delete));
                log.info("Removed a notification device that is no longer subscribed");
            } else {
                log.warn("Push service answered {} for device {}", code, d.getId());
            }
        } catch (Exception e) {
            log.warn("Notification to device {} failed: {}", d.getId(), e.getMessage());
        }
        return false;
    }

    /** Sends the request, once more on a rate limit or server error. Overridden in tests. */
    protected int deliver(HttpRequest req) throws Exception {
        int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (code == 429 || code >= 500) {
            Thread.sleep(3000);
            code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
        return code;
    }

    static String checkEndpoint(String endpoint) {
        String e = endpoint == null ? "" : endpoint.trim();
        URI u;
        try {
            u = URI.create(e);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("This browser's notification address is not valid");
        }
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        boolean known = PUSH_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!"https".equals(u.getScheme()) || !known || e.length() > 1000) {
            throw new BusinessException("This browser's notification service is not supported");
        }
        return e;
    }

    static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** "Android · Chrome", "iPhone · Safari": so the owner can tell their devices apart. */
    static String label(String ua) {
        String u = ua == null ? "" : ua;
        String os = u.contains("iPhone") ? "iPhone" : u.contains("iPad") ? "iPad" : u.contains("Android") ? "Android"
                : u.contains("Windows") ? "Windows" : u.contains("Mac OS") ? "Mac" : "Device";
        String br = u.contains("Edg/") ? "Edge" : u.contains("SamsungBrowser") ? "Samsung Internet" : u.contains("Firefox") ? "Firefox"
                : u.contains("Chrome") ? "Chrome" : u.contains("Safari") ? "Safari" : "Browser";
        return os + " · " + br;
    }

}
