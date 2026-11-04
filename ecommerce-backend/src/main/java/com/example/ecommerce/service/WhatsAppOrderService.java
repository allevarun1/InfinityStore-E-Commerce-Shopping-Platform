package com.example.ecommerce.service;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WhatsApp message on the phone when an order is paid: an alert to the shop owner's number(s) and a confirmation
 * to the customer, through Meta's WhatsApp Cloud API (two approved "utility" templates).
 *
 * <ul>
 *   <li>Only for real orders: called after the payment is confirmed (or when a cash-on-delivery order is placed).</li>
 *   <li>Once per order and per recipient, even when Razorpay reports the same payment several times.</li>
 *   <li>Runs after the order is saved, on its own thread: a WhatsApp problem never blocks or undoes an order.
 *       It retries, and a send that still fails is shown in admin with a Resend button.</li>
 *   <li>Off (does nothing) until WHATSAPP_TOKEN and WHATSAPP_PHONE_NUMBER_ID are set.</li>
 * </ul>
 */
@Service
public class WhatsAppOrderService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppOrderService.class);
    /** Template values may not contain new lines, tabs or more than four spaces in a row. */
    private static final int MAX_VALUE = 900;

    private final String token;
    private final String phoneNumberId;
    private final List<String> ownerNumbers;
    private final boolean notifyOwner;
    private final boolean notifyCustomer;
    private final String ownerTemplate;
    private final String customerTemplate;
    private final String language;
    private final String frontendUrl;
    private final long[] retryDelaysMs;
    private final OrderRepository orders;
    private final TransactionTemplate tx;
    private final RestClient client;
    private final MailService mail;
    private final String ownerEmail;

    public WhatsAppOrderService(@Value("${app.whatsapp.token:}") String token,
                                @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                                @Value("${app.whatsapp.order-alert-to:}") String ownerNumbers,
                                @Value("${app.whatsapp.notify-owner:true}") boolean notifyOwner,
                                @Value("${app.whatsapp.notify-customer:true}") boolean notifyCustomer,
                                @Value("${app.whatsapp.owner-template:new_order_alert}") String ownerTemplate,
                                @Value("${app.whatsapp.customer-template:order_confirmed}") String customerTemplate,
                                @Value("${app.whatsapp.order-language:en}") String language,
                                @Value("${app.frontend-url:}") String frontendUrl,
                                @Value("${app.whatsapp.retry-delays-ms:2000,10000,30000}") long[] retryDelaysMs,
                                OrderRepository orders, PlatformTransactionManager txManager) {
        this(token, phoneNumberId, ownerNumbers, notifyOwner, notifyCustomer, ownerTemplate, customerTemplate, language,
                frontendUrl, retryDelaysMs, orders, txManager, null, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WhatsAppOrderService(@Value("${app.whatsapp.token:}") String token,
                                @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                                @Value("${app.whatsapp.order-alert-to:}") String ownerNumbers,
                                @Value("${app.whatsapp.notify-owner:true}") boolean notifyOwner,
                                @Value("${app.whatsapp.notify-customer:true}") boolean notifyCustomer,
                                @Value("${app.whatsapp.owner-template:new_order_alert}") String ownerTemplate,
                                @Value("${app.whatsapp.customer-template:order_confirmed}") String customerTemplate,
                                @Value("${app.whatsapp.order-language:en}") String language,
                                @Value("${app.frontend-url:}") String frontendUrl,
                                @Value("${app.whatsapp.retry-delays-ms:2000,10000,30000}") long[] retryDelaysMs,
                                OrderRepository orders, PlatformTransactionManager txManager,
                                MailService mail, @Value("${app.order-alert-email:}") String ownerEmail) {
        this.mail = mail;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim();
        this.token = token == null ? "" : token.trim();
        this.phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.trim();
        this.ownerNumbers = parseNumbers(ownerNumbers);
        this.notifyOwner = notifyOwner;
        this.notifyCustomer = notifyCustomer;
        this.ownerTemplate = blank(ownerTemplate) ? "new_order_alert" : ownerTemplate.trim();
        this.customerTemplate = blank(customerTemplate) ? "order_confirmed" : customerTemplate.trim();
        this.language = blank(language) ? "en" : language.trim();
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.trim().replaceAll("/+$", "");
        this.retryDelaysMs = retryDelaysMs == null ? new long[0] : retryDelaysMs;
        this.orders = orders;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setReadOnly(true);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl("https://graph.facebook.com/v21.0").requestFactory(factory).build();
        if (enabled()) {
            log.info("WhatsApp order alerts on: owner {} ({} number(s)), customer {}", notifyOwner, this.ownerNumbers.size(), notifyCustomer);
        }
    }

    public boolean enabled() {
        return !token.isEmpty() && !phoneNumberId.isEmpty();
    }

    /** What admin shows next to an order: SENT, WAITING (not yet / failed) or OFF (not set up / not wanted). */
    public Status status(Order o) {
        String owner = !enabled() || !notifyOwner || ownerNumbers.isEmpty() ? "OFF" : o.getWhatsappOwnerAt() != null ? "SENT" : "WAITING";
        String customer = !enabled() || !notifyCustomer || o.getPhoneNumber() == null ? "OFF" : o.getWhatsappCustomerAt() != null ? "SENT" : "WAITING";
        return new Status(owner, customer, o.getWhatsappError());
    }

    public record Status(String owner, String customer, String error) {
    }

    /** Sends whatever has not been sent yet for this paid order. Safe to call any number of times. */
    @Async
    public void orderPaid(Long orderId) {
        if (!enabled()) return;
        Message m = tx.execute(s -> orders.findById(orderId).map(this::message).orElse(null));
        if (m == null) return;
        List<String> problems = new ArrayList<>();

        if (notifyOwner && !ownerNumbers.isEmpty() && orders.claimWhatsappOwner(orderId, LocalDateTime.now()) == 1) {
            int sent = 0;
            for (String to : ownerNumbers) {
                String err = sendWithRetry(to, ownerTemplate, m.ownerValues());
                if (err == null) sent++; else problems.add("owner " + mask(to) + ": " + err);
            }
            if (sent == 0) orders.releaseWhatsappOwner(orderId); // nobody got it: allow Resend
        }
        if (notifyCustomer && m.customerPhone() != null && orders.claimWhatsappCustomer(orderId, LocalDateTime.now()) == 1) {
            String err = sendWithRetry(m.customerPhone(), customerTemplate, m.customerValues());
            if (err != null) {
                problems.add("customer: " + err);
                orders.releaseWhatsappCustomer(orderId);
            }
        }
        String error = problems.isEmpty() ? null : String.join("; ", problems);
        orders.setWhatsappError(orderId, error == null ? null : error.substring(0, Math.min(300, error.length())));
        if (error != null) log.warn("WhatsApp order alert for order {} not fully sent: {}", orderId, error);
    }

    /**
     * Email to the owner for a new paid order, so the phone's mail app notifies them too (works without WhatsApp).
     * Called once, when the order is paid (or placed with cash on delivery).
     */
    @Async
    public void emailOwner(Long orderId) {
        if (mail == null || ownerEmail.isEmpty()) return;
        Message m = tx.execute(s -> orders.findById(orderId).map(this::message).orElse(null));
        if (m == null) return;
        List<String> v = m.ownerValues();
        mail.send(ownerEmail, "New paid order " + v.get(0) + " - " + v.get(4),
                String.join("\n",
                        "You have a new order on Rekha Shopping Mall.", "",
                        "Order: " + v.get(0), "Customer: " + v.get(1), "Phone: " + v.get(2),
                        "Sarees: " + v.get(3), "Amount: " + v.get(4), "Deliver to: " + v.get(5), "",
                        "Open the admin to pack and ship it" + (frontendUrl.isEmpty() ? "." : ": " + frontendUrl + "/admin?tab=orders")));
    }

    record Message(List<String> ownerValues, List<String> customerValues, String customerPhone) {
    }

    Message message(Order o) {
        String number = (o.isTestPayment() ? "TEST " : "") + o.getTrackingNumber();
        var u = o.getUser();
        String full = u == null ? "" : (nz(u.getFirstName()) + " " + nz(u.getLastName())).trim();
        String name = clean(!full.isEmpty() ? full : u != null && u.getUsername() != null ? u.getUsername() : "Customer");
        String items = clean(o.getOrderItems().stream().map(WhatsAppOrderService::itemText).collect(Collectors.joining(", ")));
        String amount = "Rs. " + o.getTotalPrice().setScale(0, RoundingMode.HALF_UP).toPlainString()
                + ("ONLINE".equals(o.getPaymentMethod()) ? " paid online" : " cash on delivery");
        String phone = digits(o.getPhoneNumber());
        String track = frontendUrl.isEmpty() ? "your account" : frontendUrl + "/orders";
        // new_order_alert: {{1}} order, {{2}} customer, {{3}} phone, {{4}} items, {{5}} amount, {{6}} address
        List<String> owner = List.of(number, name, phone == null ? "-" : "+91 " + phone, items, amount, clean(o.getShippingAddress()));
        // order_confirmed: {{1}} name, {{2}} order, {{3}} items, {{4}} amount, {{5}} where to track
        List<String> customer = List.of(name, number, items, amount, track);
        return new Message(owner, customer, phone);
    }

    private static String itemText(OrderItem i) {
        String n = i.getProductName() != null ? i.getProductName() : i.getProduct() != null ? i.getProduct().getName() : "Saree";
        return i.getQuantity() > 1 ? n + " x" + i.getQuantity() : n;
    }

    /** Null when sent; otherwise the last error. Waits between tries (2 s, 10 s, 30 s by default). */
    private String sendWithRetry(String to, String template, List<String> values) {
        String err = null;
        for (int attempt = 0; attempt <= retryDelaysMs.length; attempt++) {
            err = send(to, template, values);
            if (err == null || err.startsWith("rejected")) return err; // a rejected template or number won't fix itself
            if (attempt < retryDelaysMs.length) {
                try {
                    Thread.sleep(retryDelaysMs[attempt]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return err;
                }
            }
        }
        return err;
    }

    protected String send(String to, String template, List<String> values) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", "91" + to,
                "type", "template",
                "template", Map.of(
                        "name", template,
                        "language", Map.of("code", language),
                        "components", List.of(Map.of("type", "body", "parameters",
                                values.stream().map(v -> Map.of("type", "text", "text", v)).toList()))));
        try {
            client.post().uri("/{id}/messages", phoneNumberId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 4xx other than rate limits: wrong template, number not on WhatsApp, expired token. Retrying won't help.
            if (e.getStatusCode().value() == 429) return "rate limited by WhatsApp";
            return "rejected (" + e.getStatusCode().value() + "): " + shorten(e.getResponseBodyAsString());
        } catch (RestClientException e) {
            return "not reachable: " + shorten(e.getMessage());
        }
    }

    public static List<String> parseNumbers(String raw) {
        if (raw == null) return List.of();
        return Arrays.stream(raw.split("[,;/]+")).map(WhatsAppOrderService::digits).filter(d -> d != null).distinct().toList();
    }

    /** 10-digit Indian mobile, or null. Accepts +91 / 91 / 0 prefixes and spaces. */
    static String digits(String phone) {
        if (phone == null) return null;
        String d = phone.replaceAll("\\D", "");
        if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
        return d.matches("[6-9]\\d{9}") ? d : null;
    }

    public static String clean(String v) {
        String s = v == null ? "" : v.replaceAll("[\\r\\n\\t]+", ", ").replaceAll(" {2,}", " ").trim();
        return s.length() > MAX_VALUE ? s.substring(0, MAX_VALUE - 1) + "…" : s.isEmpty() ? "-" : s;
    }

    private static String mask(String n) {
        return "xxxxxx" + n.substring(n.length() - 4);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ");
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
