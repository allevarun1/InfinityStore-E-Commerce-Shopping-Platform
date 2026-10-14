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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
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
    private AuditService auditService;

    @Autowired
    private MailService mailService;

    public PaymentOptions paymentOptions() {
        return new PaymentOptions(true, paymentGateway.enabled(), paymentGateway.testMode());
    }

    public PriceQuote quoteCart(User user) {
        return pricingService.quote(cartItemRepository.findByUser(user));
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
            throw new BusinessException("Online payment is not available yet. Please choose Cash on delivery.");
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

        PriceQuote quote = pricingService.quote(cartItems);

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

        List<OrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem cartItem = cartItems.get(i);
            PriceQuote.Line line = quote.lines().get(i);
            Product product = cartItem.getProduct();
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            OrderItem orderItem = new OrderItem(order, product, cartItem.getQuantity(), line.unitPrice());
            orderItem.setProductName(product.getName());
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
        order.setPaymentStatus(next);
        Order saved = orderRepository.save(order);
        auditService.record("PAYMENT_STATUS_CHANGED", "ORDER", saved.getId(), current + " -> " + next);
        if ("PAID".equals(next)) {
            afterCommit(() -> mailService.sendPaymentSuccess(saved.getUser().getEmail(), saved));
        }
        return saved;
    }

    public List<String> nextStatuses(Order order) {
        return ORDER_TRANSITIONS.getOrDefault(order.getStatus(), Set.of()).stream().sorted().toList();
    }

    private Order applyOrderStatus(Order order, String next) {
        String current = order.getStatus();
        if (!ORDER_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new BusinessException("Order cannot move from " + current + " to " + next);
        }
        if ("CANCELLED".equals(next)) {
            restoreStock(order);
        }
        order.setStatus(next);
        Order saved = orderRepository.save(order);
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
