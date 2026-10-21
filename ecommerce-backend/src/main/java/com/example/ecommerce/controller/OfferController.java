package com.example.ecommerce.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.model.BundleOffer;
import com.example.ecommerce.service.MultiBuyService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Multi-buy tiers: public list for the storefront, management for the owner. */
@RestController
public class OfferController {
    public record Tier(Long id, int minItems, BigDecimal percentOff, String label, boolean active) {
        static Tier of(BundleOffer o) { return new Tier(o.getId(), o.getMinItems(), o.getPercentOff(), o.label(), o.isActive()); }
    }

    public record TierRequest(@Min(2) @Max(20) int minItems, @DecimalMin("1") @DecimalMax("50") BigDecimal percentOff) {}

    @Autowired private MultiBuyService offers;

    @GetMapping("/api/store/offers")
    public ResponseEntity<List<Tier>> publicTiers() {
        return ResponseEntity.ok(offers.active().stream().map(Tier::of).toList());
    }

    @GetMapping("/api/admin/offers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Tier>> all() {
        return ResponseEntity.ok(offers.all().stream().map(Tier::of).toList());
    }

    @PostMapping("/api/admin/offers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Tier> create(@Valid @RequestBody TierRequest r) {
        return ResponseEntity.ok(Tier.of(offers.create(r.minItems(), r.percentOff())));
    }

    @PutMapping("/api/admin/offers/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Tier> setActive(@PathVariable Long id, @RequestParam boolean value) {
        return ResponseEntity.ok(Tier.of(offers.setActive(id, value)));
    }

    @DeleteMapping("/api/admin/offers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id) {
        offers.delete(id);
        return ResponseEntity.ok(new MessageResponse("Offer removed"));
    }
}
