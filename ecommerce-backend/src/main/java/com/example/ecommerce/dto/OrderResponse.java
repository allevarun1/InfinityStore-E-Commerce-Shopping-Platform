package com.example.ecommerce.dto;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Public representation of an order. Never exposes persistence entities or credentials. */
public record OrderResponse(
        Long id,
        String trackingNumber,
        LocalDateTime orderDate,
        String status,
        String paymentMethod,
        String paymentStatus,
        boolean testPayment,
        String shippingAddress,
        String phoneNumber,
        String currency,
        Integer pricingVersion,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal shippingAmount,
        BigDecimal totalPrice,
        List<Item> orderItems,
        List<String> nextStatuses,
        Customer user,
        String couponCode,
        BigDecimal couponDiscountAmount,
        String offerLabel,
        BigDecimal offerDiscountAmount,
        /* admin only: whether the WhatsApp order messages went out */
        WhatsApp whatsapp,
        /* online orders only: what the storefront needs to open the payment window straight away */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        com.example.ecommerce.service.payment.PaymentGateway.CheckoutSession payment) {

    /** The same order with the payment-window details attached, so checkout needs no second round trip. */
    public OrderResponse withPayment(com.example.ecommerce.service.payment.PaymentGateway.CheckoutSession session) {
        return new OrderResponse(id, trackingNumber, orderDate, status, paymentMethod, paymentStatus, testPayment,
                shippingAddress, phoneNumber, currency, pricingVersion, subtotalAmount, discountAmount, taxAmount,
                shippingAmount, totalPrice, orderItems, nextStatuses, user, couponCode, couponDiscountAmount,
                offerLabel, offerDiscountAmount, whatsapp, session);
    }

    /** SENT, WAITING or OFF for the owner alert and the customer confirmation, plus the last problem if any. */
    public record WhatsApp(String owner, String customer, String error) {
    }

    public record Item(Long id, ProductRef product, int quantity, BigDecimal price, BigDecimal taxPercent) {
    }

    public record ProductRef(Long id, String name, String imageUrl) {
    }

    /** Only populated for administrators. */
    public record Customer(Long id, String username, String email, String firstName, String lastName) {
    }

    public static OrderResponse forCustomer(Order order, List<String> nextStatuses) {
        return build(order, nextStatuses, null, null);
    }

    public static OrderResponse forAdmin(Order order, List<String> nextStatuses) {
        return forAdmin(order, nextStatuses, null);
    }

    public static OrderResponse forAdmin(Order order, List<String> nextStatuses, WhatsApp whatsapp) {
        var u = order.getUser();
        return build(order, nextStatuses,
                new Customer(u.getId(), u.getUsername(), u.getEmail(), u.getFirstName(), u.getLastName()), whatsapp);
    }

    private static OrderResponse build(Order o, List<String> next, Customer customer, WhatsApp whatsapp) {
        List<Item> items = o.getOrderItems().stream().map(OrderResponse::item).toList();
        return new OrderResponse(o.getId(), o.getTrackingNumber(), o.getOrderDate(), o.getStatus(),
                o.getPaymentMethod(), o.getPaymentStatus(), o.isTestPayment(), o.getShippingAddress(),
                o.getPhoneNumber(), o.getCurrency(), o.getPricingVersion(), o.getSubtotalAmount(),
                o.getDiscountAmount(), o.getTaxAmount(), o.getShippingAmount(), o.getTotalPrice(), items, next,
                customer, o.getCouponCode(), o.getCouponDiscountAmount(), o.getOfferLabel(), o.getOfferDiscountAmount(), whatsapp, null);
    }

    private static Item item(OrderItem i) {
        var p = i.getProduct();
        String name = i.getProductName() != null ? i.getProductName() : p.getName();
        return new Item(i.getId(), new ProductRef(p.getId(), name, p.getImageUrl()), i.getQuantity(), i.getPrice(),
                i.getTaxPercent());
    }
}
