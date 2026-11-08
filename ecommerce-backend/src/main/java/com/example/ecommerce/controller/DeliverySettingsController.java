package com.example.ecommerce.controller;

import com.example.ecommerce.service.AuditService;
import com.example.ecommerce.service.StorePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** The owner's delivery charges, changeable at any time from the admin dashboard. */
@RestController
@RequestMapping("/api/admin/delivery-settings")
@PreAuthorize("hasRole('ADMIN')")
public class DeliverySettingsController {
    @Autowired
    private StorePolicy policy;

    @Autowired
    private AuditService audit;

    /** freeAbove: orders above this amount ship free; empty or 0 means delivery is never free. */
    public record Request(@NotNull BigDecimal fee, BigDecimal freeAbove) {
    }

    public record View(BigDecimal fee, BigDecimal freeAbove, BigDecimal defaultFee, BigDecimal defaultFreeAbove) {
    }

    private View view() {
        return new View(policy.shippingFee(), policy.freeShippingThreshold(),
                policy.defaultShippingFee(), policy.defaultFreeShippingThreshold());
    }

    @GetMapping
    public ResponseEntity<View> get() {
        return ResponseEntity.ok(view());
    }

    @PutMapping
    @Transactional
    public ResponseEntity<View> update(@Valid @RequestBody Request r) {
        policy.updateDelivery(r.fee(), r.freeAbove());
        audit.record("DELIVERY_CHARGES_CHANGED", "Settings", null,
                "fee=" + policy.shippingFee() + ", freeAbove=" + policy.freeShippingThreshold());
        return ResponseEntity.ok(view());
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<View> reset() {
        policy.resetDelivery();
        audit.record("DELIVERY_CHARGES_RESET", "Settings", null, "back to the environment defaults");
        return ResponseEntity.ok(view());
    }
}
