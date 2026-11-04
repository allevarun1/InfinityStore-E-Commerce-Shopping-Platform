package com.example.ecommerce.controller;

import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.dto.StatusUpdateRequest;
import com.example.ecommerce.dto.UserResponse;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.services.UserDetailsImpl;
import com.example.ecommerce.service.AccountService;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    @Autowired
    private com.example.ecommerce.service.WhatsAppOrderService whatsAppOrders;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserResponse>> users() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(UserResponse::from).toList());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (((UserDetailsImpl) auth.getPrincipal()).getId().equals(id)) {
            throw new BusinessException("You cannot delete your own account");
        }
        accountService.deleteAccount(id);
        return ResponseEntity.ok(new MessageResponse("User deleted"));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> orders() {
        return ResponseEntity.ok(orderService.getAllOrders().stream().map(this::view).toList());
    }

    @PutMapping("/orders/{id}/delivery-status")
    public ResponseEntity<OrderResponse> deliveryStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(view(orderService.updateOrderStatus(id, request.getStatus())));
    }

    @PutMapping("/orders/{id}/payment-status")
    public ResponseEntity<OrderResponse> paymentStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(view(orderService.updatePaymentStatus(id, request.getStatus())));
    }

    /** Sends the paid-order WhatsApp messages that have not gone out yet (after fixing a number or template). */
    @PostMapping("/orders/{id}/whatsapp-resend")
    public ResponseEntity<MessageResponse> resendWhatsapp(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        if (!whatsAppOrders.enabled()) {
            throw new BusinessException("WhatsApp is not set up yet. Add WHATSAPP_TOKEN and WHATSAPP_PHONE_NUMBER_ID on the server first.");
        }
        boolean real = "PAID".equals(order.getPaymentStatus()) || ("COD".equals(order.getPaymentMethod()) && !"CANCELLED".equals(order.getStatus()));
        if (!real) {
            throw new BusinessException("Only paid orders get WhatsApp messages");
        }
        whatsAppOrders.orderPaid(id);
        return ResponseEntity.ok(new MessageResponse("Sending now. Refresh in a minute to see the result."));
    }

    private OrderResponse view(Order order) {
        var s = whatsAppOrders.status(order);
        return OrderResponse.forAdmin(order, orderService.nextStatuses(order), new OrderResponse.WhatsApp(s.owner(), s.customer(), s.error()));
    }
}
