package com.example.ecommerce.service;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.PaymentOptions;
import com.example.ecommerce.dto.PriceQuote;
import com.example.ecommerce.model.*;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.payment.PaymentGateway;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class OrderService {
    public static final int PRICING_VERSION = 1;
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** An online order not paid within this time is cancelled and its stock goes back on sale. */
    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(45);

    // Fulfilment state machine. DELIVERED and CANCELLED are terminal.
    private static final Map<String, Set<String>> ORDER_TRANSITIONS = Map.of(
            "PENDING", Set.of("CONFIRMED", "SHIPPED", "CANCELLED"),
            "CONFIRMED", Set.of("SHIPPED", "CANCELLED"),
            "SHIPPED", Set.of("DELIVERED"),
            "DELIVERED", Set.of(),
            "CANCELLED", Set.of());

    private static final Map<String, Set<String>> PAYMENT_TRANSITIONS = Map.of(
            "PENDING", Set.of("PAID", "FAILED"),
            "FAILED", Set.of(),
            "PAID", Set.of("REFUNDED"),
            "REFUNDED", Set.of());

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private StorePolicy policy;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MailService mailService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private ReferralService referralService;

    public PaymentOptions paymentOptions() {
        return new PaymentOptions(policy.codEnabled(), paymentGateway.enabled(), paymentGateway.testMode());
    }

    public PriceQuote quoteCart(User user) {
        return quoteCart(user, null);
    }

    /** Previews the price with an optional coupon; an invalid code is reported as a BusinessException. */
    public PriceQuote quoteCart(User user, String couponCode) {
        List<CartItem> items = cartItemRepository.findByUser(user);
        return priceWithCoupon(user, items, couponCode, false).quote();
    }

    private record Priced(PriceQuote quote, CouponService.Applied applied) {
    }

    private Priced priceWithCoupon(User user, List<CartItem> items, String couponCode, boolean lock) {
        PriceQuote base = pricingService.quote(items);
        if (couponCode == null || couponCode.isBlank()) {
            return new Priced(base, null);
        }
        CouponService.Applied applied = couponService.evaluate(user, couponCode, base.taxableAmount(), lock);
        return new Priced(pricingService.quote(items, applied.discount(), applied.coupon().getCode()), applied);
    }

    public Order placeOrder(User user, OrderRequest request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);

        // Serialise this customer's checkouts: a double submit waits here, then either finds
        // the order created by the first request (same key) or an empty cart.
        User owner = userRepository.lockById(user.getId()).orElseThrow(() -> new BusinessException("User not found"));
        if (key != null) {
            var existing = orderRepository.findByUserAndIdempotencyKey(owner, key);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        List<CartItem> cartItems = new ArrayList<>(cartItemRepository.findByUser(owner));
        if (cartItems.isEmpty()) {
            throw new BusinessException("Your cart is empty");
        }

        boolean online = isOnline(request.getPaymentMethod());
        if (online && !paymentGateway.enabled()) {
            throw new BusinessException(policy.codEnabled()
                    ? "Online payment is not available yet. Please choose Cash on delivery."
                    : "Online payment is not available right now. Please try again later.");
        }
        if (!online && !policy.codEnabled()) {
            throw new BusinessException("Cash on delivery is not available. Please pay online.");
        }

        // Lock product rows in a stable order (prevents deadlocks) and re-read their latest state.
        cartItems.sort(Comparator.comparing(ci -> ci.getProduct().getId()));
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            em.refresh(product, LockModeType.PESSIMISTIC_WRITE);
            if (!product.isPublished()) {
                throw new BusinessException("'" + product.getName() + "' is no longer available");
            }
            if (product.getStockQuantity() == null || product.getStockQuantity() < item.getQuantity()) {
                throw new BusinessException("Insufficient stock for product: " + product.getName());
            }
        }

        Priced priced = priceWithCoupon(owner, cartItems, request.getCouponCode(), true);
        PriceQuote quote = priced.quote();
        if (request.getExpectedTotal() != null && request.getExpectedTotal().compareTo(quote.total()) != 0) {
            throw new BusinessException("Prices or shipping changed since you opened checkout. "
                    + "Please review the updated total and place the order again.");
        }

        Order order = new Order();
        order.setUser(owner);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus("PENDING");
        order.setShippingAddress(request.getShippingAddress().trim());
        order.setPhoneNumber(request.getPhoneNumber());
        order.setPaymentMethod(online ? "ONLINE" : "COD");
        order.setPaymentStatus("PENDING");
        order.setTrackingNumber(newOrderNumber());
        order.setIdempotencyKey(key);
        order.setPricingVersion(PRICING_VERSION);
        order.setCurrency(quote.currency());
        order.setSubtotalAmount(quote.subtotal());
        order.setDiscountAmount(quote.discount());
        order.setTaxAmount(quote.tax());
        order.setShippingAmount(quote.shipping());
        order.setTotalPrice(quote.total());
        if (quote.offerDiscount() != null && quote.offerDiscount().signum() > 0) {
            order.setOfferLabel(quote.offerLabel());
            order.setOfferDiscountAmount(quote.offerDiscount());
        }
        if (priced.applied() != null) {
            order.setCouponCode(priced.applied().coupon().getCode());
            order.setCouponDiscountAmount(quote.couponDiscount());
            couponService.recordRedemption(priced.applied().coupon());
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem cartItem = cartItems.get(i);
            PriceQuote.Line line = quote.lines().get(i);
            Product product = cartItem.getProduct();
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            OrderItem orderItem = new OrderItem(order, product, cartItem.getQuantity(), line.unitPrice());
            orderItem.setProductName(product.getName());
            orderItem.setUnitCost(product.getCostPrice());
            orderItem.setTaxPercent(line.taxPercent());
            orderItems.add(orderItem);
        }
        order.setOrderItems(orderItems);

        if (online) {
            PaymentGateway.PaymentResult result = paymentGateway.initiate(order);
            order.setPaymentReference(result.reference());
            order.setTestPayment(paymentGateway.testMode());
            if (result.status() == PaymentGateway.PaymentResult.Status.PAID) {
                order.setPaymentStatus("PAID");
            } else if (result.status() == PaymentGateway.PaymentResult.Status.FAILED) {
                throw new BusinessException("Payment could not be completed");
            }
        }

        Order saved = orderRepository.save(order);
        cartItemRepository.deleteAll(cartItems);
        auditService.record("ORDER_PLACED", "ORDER", saved.getId(),
                "total=" + saved.getTotalPrice() + " method=" + saved.getPaymentMethod() + " testPayment=" + saved.isTestPayment());

        afterCommit(() -> {
            // An online order is only a real order once it is paid: its confirmation goes out from markPaid.
            if (awaitingPayment(saved)) return;
            mailService.sendOrderPlaced(owner.getEmail(), saved);
            if ("PAID".equals(saved.getPaymentStatus()) && !saved.isTestPayment()) {
                mailService.sendPaymentSuccess(owner.getEmail(), saved);
            }
        });
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(User user) {
        return orderRepository.findByUserOrderByOrderDateDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc();
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new BusinessException("Order not found"));
    }

    /** Customers may cancel only orders that have not been paid or shipped. */
    public Order cancelByCustomer(User user, Long orderId) {
        Order order = getOrderById(orderId);
        if (!order.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Order not found");
        }
        if (!"PENDING".equals(order.getStatus()) || "PAID".equals(order.getPaymentStatus())) {
            throw new BusinessException("This order can no longer be cancelled online. Please contact support.");
        }
        return applyOrderStatus(order, "CANCELLED");
    }

    public Order updateOrderStatus(Long orderId, String status) {
        Order order = getOrderById(orderId);
        return applyOrderStatus(order, status == null ? "" : status.trim().toUpperCase());
    }

    public Order updatePaymentStatus(Long orderId, String status) {
        Order order = getOrderById(orderId);
        String next = status == null ? "" : status.trim().toUpperCase();
        String current = order.getPaymentStatus();
        if (!PAYMENT_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new BusinessException("Payment cannot move from " + current + " to " + next);
        }
        if ("PAID".equals(next)) {
            // Online payments are confirmed only by the payment provider, never by hand.
            if (!"COD".equals(order.getPaymentMethod())) {
                throw new BusinessException("Online payments can only be confirmed by the payment provider");
            }
            if ("CANCELLED".equals(order.getStatus())) {
                throw new BusinessException("A cancelled order cannot be marked as paid");
            }
        }
        String refundNote = "";
        if ("REFUNDED".equals(next) && order.getPaymentId() != null) {
            // Paid online: the money goes back through the provider, not just a status change.
            refundNote = " refund=" + paymentGateway.refund(order.getPaymentId(), PaymentGateway.paise(order.getTotalPrice()));
        }
        order.setPaymentStatus(next);
        Order saved = orderRepository.save(order);
        auditService.record("PAYMENT_STATUS_CHANGED", "ORDER", saved.getId(), current + " -> " + next + refundNote);
        if ("PAID".equals(next)) {
            afterCommit(() -> mailService.sendPaymentSuccess(saved.getUser().getEmail(), saved));
        }
        return saved;
    }

    /** Details for opening the payment window for an online order that is still waiting to be paid. */
    public PaymentGateway.CheckoutSession paymentSession(User user, Long orderId) {
        Order order = ownOrder(user, orderId);
        if (!"ONLINE".equals(order.getPaymentMethod()) || !"PENDING".equals(order.getPaymentStatus())
                || !"PENDING".equals(order.getStatus())) {
            throw new BusinessException("This order is not waiting for an online payment");
        }
        if (!paymentGateway.enabled()) {
            throw new BusinessException("Online payment is not available right now");
        }
        if (order.getOrderDate().isBefore(LocalDateTime.now().minus(PAYMENT_WINDOW))) {
            throw new BusinessException("The time to pay for this order has run out. Please place the order again.");
        }
        if (order.getPaymentReference() == null || order.getPaymentReference().isBlank()) {
            order.setPaymentReference(paymentGateway.initiate(order).reference());
            orderRepository.save(order);
        }
        return paymentGateway.checkoutSession(order);
    }

    /** The storefront's success callback: trusted only when the provider's signature checks out. */
    public Order confirmPayment(User user, Long orderId, String providerOrderId, String paymentId, String signature) {
        Order order = orderRepository.lockById(orderId)
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new BusinessException("Order not found"));
        if ("PAID".equals(order.getPaymentStatus()) || "REFUNDED".equals(order.getPaymentStatus())) {
            return order;
        }
        if (!"ONLINE".equals(order.getPaymentMethod()) || order.getPaymentReference() == null
                || !order.getPaymentReference().equals(providerOrderId)
                || !paymentGateway.verifyPayment(providerOrderId, paymentId, signature)) {
            auditService.record("PAYMENT_SIGNATURE_REJECTED", "ORDER", order.getId(), "payment=" + paymentId);
            throw new BusinessException("We could not verify this payment. If money was taken, it will be confirmed automatically within a few minutes.");
        }
        return markPaid(order, paymentId, "checkout");
    }

    /** Provider webhook (already signature-checked): marks the matching order paid. Unknown orders are ignored. */
    public void paymentCaptured(String providerOrderId, String paymentId) {
        orderRepository.findByPaymentReference(providerOrderId)
                .flatMap(o -> orderRepository.lockById(o.getId()))
                .ifPresentOrElse(o -> markPaid(o, paymentId, "webhook"),
                        () -> log.info("Payment {} for unknown provider order {} ignored", paymentId, providerOrderId));
    }

    private Order markPaid(Order order, String paymentId, String source) {
        if ("PAID".equals(order.getPaymentStatus()) || "REFUNDED".equals(order.getPaymentStatus())) {
            return order;
        }
        long amount = PaymentGateway.paise(order.getTotalPrice());
        if (!paymentGateway.capture(paymentId, order.getPaymentReference(), amount)) {
            auditService.record("PAYMENT_MISMATCH", "ORDER", order.getId(), "payment=" + paymentId + " source=" + source);
            throw new BusinessException("This payment does not match the order. Please contact support.");
        }
        order.setPaymentStatus("PAID");
        order.setPaymentId(paymentId);
        order.setTestPayment(paymentGateway.testMode());
        String note = "payment=" + paymentId + " source=" + source;
        if ("CANCELLED".equals(order.getStatus())) {
            // Paid after the order was cancelled or timed out: the items may be gone, so give the money back.
            try {
                note += " refund=" + paymentGateway.refund(paymentId, amount) + " (order was cancelled)";
                order.setPaymentStatus("REFUNDED");
            } catch (RuntimeException e) {
                note += " (order was cancelled: refund it from the Razorpay dashboard)";
            }
        }
        Order saved = orderRepository.save(order);
        auditService.record("PAYMENT_CAPTURED", "ORDER", saved.getId(), note);
        if ("PAID".equals(saved.getPaymentStatus())) {
            afterCommit(() -> {
                mailService.sendOrderPlaced(saved.getUser().getEmail(), saved);
                if (!saved.isTestPayment()) mailService.sendPaymentSuccess(saved.getUser().getEmail(), saved);
            });
        }
        return saved;
    }

    /** An online order whose payment Razorpay has not confirmed yet. It must never be packed or shipped. */
    public static boolean awaitingPayment(Order order) {
        return "ONLINE".equals(order.getPaymentMethod()) && !"PAID".equals(order.getPaymentStatus());
    }

    /**
     * Online orders left unpaid past the payment window: if the customer did pay (and we never heard back),
     * mark them paid; otherwise cancel them so the reserved stock and coupon are released.
     */
    @Scheduled(initialDelayString = "${app.payment.expiry-initial-delay-ms:180000}", fixedDelayString = "${app.payment.expiry-interval-ms:600000}")
    public void releaseUnpaidOnlineOrders() {
        if (!paymentGateway.enabled()) {
            return;
        }
        for (Order order : orderRepository.findUnpaidOnlineBefore(LocalDateTime.now().minus(PAYMENT_WINDOW))) {
            try {
                String paid = order.getPaymentReference() == null ? null : paymentGateway.findCapturedPayment(order.getPaymentReference());
                if (paid != null) {
                    markPaid(order, paid, "reconcile");
                } else {
                    applyOrderStatus(order, "CANCELLED");
                    order.setPaymentStatus("FAILED");
                    orderRepository.save(order);
                    auditService.record("PAYMENT_EXPIRED", "ORDER", order.getId(), "not paid within " + PAYMENT_WINDOW.toMinutes() + " minutes");
                }
            } catch (RuntimeException e) {
                log.warn("Could not settle unpaid order {}: {}", order.getTrackingNumber(), e.getMessage());
            }
        }
    }

    private Order ownOrder(User user, Long orderId) {
        Order order = getOrderById(orderId);
        if (!order.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Order not found");
        }
        return order;
    }

    public List<String> nextStatuses(Order order) {
        return ORDER_TRANSITIONS.getOrDefault(order.getStatus(), Set.of()).stream()
                .filter(s -> !awaitingPayment(order) || "CANCELLED".equals(s))
                .sorted().toList();
    }

    private Order applyOrderStatus(Order order, String next) {
        String current = order.getStatus();
        if (!ORDER_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new BusinessException("Order cannot move from " + current + " to " + next);
        }
        if (awaitingPayment(order) && !"CANCELLED".equals(next)) {
            // Strict rule: nothing is confirmed, packed or shipped until Razorpay has confirmed the money.
            throw new BusinessException("This order has not been paid yet. It can only be confirmed or shipped after the payment is received.");
        }
        if ("CANCELLED".equals(next)) {
            restoreStock(order);
            if (order.getCouponCode() != null) {
                couponService.releaseRedemption(order.getCouponCode());
            }
        }
        order.setStatus(next);
        order.setStatusChangedAt(java.time.LocalDateTime.now());
        Order saved = orderRepository.save(order);
        if ("DELIVERED".equals(next)) {
            referralService.rewardReferrerFor(saved);
        }
        String note = "CANCELLED".equals(next) && "PAID".equals(saved.getPaymentStatus())
                ? " (payment is PAID: a refund must be issued and recorded)" : "";
        auditService.record("ORDER_STATUS_CHANGED", "ORDER", saved.getId(), current + " -> " + next + note);
        return saved;
    }

    private void restoreStock(Order order) {
        List<OrderItem> items = new ArrayList<>(order.getOrderItems());
        items.sort(Comparator.comparing(i -> i.getProduct().getId()));
        for (OrderItem item : items) {
            Product product = item.getProduct();
            em.refresh(product, LockModeType.PESSIMISTIC_WRITE);
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
        }
    }

    private static boolean isOnline(String method) {
        return method != null && !"COD".equalsIgnoreCase(method.trim());
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > 100) {
            throw new BusinessException("Invalid idempotency key");
        }
        return trimmed;
    }

    private String newOrderNumber() {
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "INF-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
    }

    /** Defers side effects (emails) until the order transaction has actually committed. */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
