package com.example.ecommerce.controller;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.dto.PaymentOptions;
import com.example.ecommerce.dto.StatusUpdateRequest;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.services.UserDetailsImpl;
import com.example.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new com.example.ecommerce.service.BusinessException("User not found"));
    }

    private OrderResponse customerView(Order order) {
        return OrderResponse.forCustomer(order, orderService.nextStatuses(order));
    }

    @GetMapping("/payment-options")
    @PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
    public ResponseEntity<PaymentOptions> paymentOptions() {
        return ResponseEntity.ok(orderService.paymentOptions());
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(customerView(orderService.placeOrder(user, request, idempotencyKey)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getOrderHistory() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(orderService.getOrdersByUser(user).stream().map(this::customerView).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        try {
            Order order = orderService.getOrderById(id);
            User user = getAuthenticatedUser();
            boolean isAdmin = user.getRoles().stream()
                    .anyMatch(role -> role.getName().name().equals("ROLE_ADMIN"));
            if (isAdmin) {
                return ResponseEntity.ok(OrderResponse.forAdmin(order, orderService.nextStatuses(order)));
            }
            if (order.getUser().getId().equals(user.getId())) {
                return ResponseEntity.ok(customerView(order));
            }
            // Do not reveal that the order exists.
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(customerView(orderService.cancelByCustomer(user, id)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders().stream()
                .map(o -> OrderResponse.forAdmin(o, orderService.nextStatuses(o))).toList());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        Order order = orderService.updateOrderStatus(id, request.getStatus());
        return ResponseEntity.ok(OrderResponse.forAdmin(order, orderService.nextStatuses(order)));
    }
}
