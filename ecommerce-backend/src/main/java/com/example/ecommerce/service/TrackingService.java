package com.example.ecommerce.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.repository.OrderRepository;

/**
 * "Track my order" without signing in. The order number alone is not enough: the mobile number given at
 * checkout must match too, and a miss never says which of the two was wrong. No address is ever returned.
 */
@Service
public class TrackingService {
    public record Item(String name, int quantity, Long productId) {}

    public record Status(String orderNumber, String status, String paymentMethod, String paymentStatus,
                         LocalDateTime placedAt, LocalDateTime updatedAt, List<Item> items, java.math.BigDecimal total) {}

    @Autowired private OrderRepository orders;

    @Transactional(readOnly = true)
    public Status track(String orderNumber, String phone) {
        String number = orderNumber == null ? "" : orderNumber.trim().toUpperCase();
        String digits = last10(phone);
        Optional<Order> found = number.isEmpty() || digits.length() != 10 ? Optional.empty() : orders.findByTrackingNumber(number);
        if (found.isEmpty() || !digits.equals(last10(found.get().getPhoneNumber()))) {
            throw new BusinessException("We couldn't find an order with those details. Check the order number from your confirmation and the mobile number you used.");
        }
        Order o = found.get();
        List<Item> items = o.getOrderItems().stream().map(this::item).toList();
        LocalDateTime updated = o.getStatusChangedAt() != null ? o.getStatusChangedAt() : o.getOrderDate();
        return new Status(o.getTrackingNumber(), o.getStatus(), o.getPaymentMethod(), o.getPaymentStatus(),
                o.getOrderDate(), updated, items, o.getTotalPrice());
    }

    private Item item(OrderItem i) {
        String name = i.getProductName() != null ? i.getProductName() : i.getProduct().getName();
        return new Item(name, i.getQuantity(), i.getProduct().getId());
    }

    static String last10(String phone) {
        if (phone == null) return "";
        String d = phone.replaceAll("\\D", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }
}
