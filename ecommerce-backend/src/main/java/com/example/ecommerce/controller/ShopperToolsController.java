package com.example.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.service.DeliveryService;
import com.example.ecommerce.service.TrackingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public shopper tools that need no account: delivery estimate by PIN code, and order tracking. */
@RestController
public class ShopperToolsController {
    public record TrackRequest(@NotBlank @Size(max = 40) String orderNumber, @NotBlank @Size(max = 20) String phone) {}

    @Autowired private DeliveryService delivery;
    @Autowired private TrackingService tracking;

    @GetMapping("/api/store/delivery")
    public ResponseEntity<DeliveryService.Estimate> estimate(@RequestParam String pincode) {
        return ResponseEntity.ok(delivery.estimate(pincode));
    }

    /** POST so the order number and phone never appear in URLs, logs or browser history. */
    @PostMapping("/api/store/track")
    public ResponseEntity<TrackingService.Status> track(@Valid @RequestBody TrackRequest r) {
        return ResponseEntity.ok(tracking.track(r.orderNumber(), r.phone()));
    }
}
