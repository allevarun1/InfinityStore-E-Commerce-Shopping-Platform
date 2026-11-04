package com.example.ecommerce.controller;

import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.security.services.UserDetailsImpl;
import com.example.ecommerce.service.push.PushService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Turning the owner's new-order phone notifications on and off. Admins only; customers get no notifications. */
@RestController
public class PushController {
    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    public record Keys(String p256dh, String auth) {
    }

    public record SubscribeRequest(String endpoint, Keys keys) {
    }

    public record EndpointRequest(String endpoint) {
    }

    @GetMapping("/api/store/push-key")
    public ResponseEntity<Map<String, String>> key() {
        // Never cached: a phone that subscribes with an out-of-date key would silently stop getting alerts.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("publicKey", push.vapid().publicKey()));
    }

    @PostMapping("/api/admin/push/devices")
    public ResponseEntity<MessageResponse> subscribe(@RequestBody SubscribeRequest r, HttpServletRequest http) {
        UserDetailsImpl me = me();
        if (r.keys() == null) throw new com.example.ecommerce.service.BusinessException("Missing notification keys");
        push.register(me.getId(), true, new PushService.Subscription(r.endpoint(), r.keys().p256dh(), r.keys().auth()),
                http.getHeader("User-Agent"));
        return ResponseEntity.ok(new MessageResponse("Notifications are on for this device"));
    }

    @PostMapping("/api/admin/push/devices/remove")
    public ResponseEntity<MessageResponse> unsubscribe(@RequestBody EndpointRequest r) {
        push.unregister(me().getId(), r.endpoint());
        return ResponseEntity.ok(new MessageResponse("Notifications are off for this device"));
    }

    @PostMapping("/api/admin/push/test")
    public ResponseEntity<MessageResponse> test(@RequestBody EndpointRequest r) {
        boolean ok = push.test(me().getId(), r.endpoint());
        return ResponseEntity.ok(new MessageResponse(ok ? "Test notification sent. It should appear in a few seconds."
                : "The notification service did not accept it. Turn notifications off and on again."));
    }

    @GetMapping("/api/admin/push/devices")
    public ResponseEntity<Map<String, Long>> adminDevices() {
        return ResponseEntity.ok(Map.of("devices", push.adminDeviceCount()));
    }

    private static UserDetailsImpl me() {
        return (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
