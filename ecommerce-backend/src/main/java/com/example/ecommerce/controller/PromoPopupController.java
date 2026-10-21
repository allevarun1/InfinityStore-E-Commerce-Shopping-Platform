package com.example.ecommerce.controller;

import com.example.ecommerce.model.PromoPopup;
import com.example.ecommerce.service.PromoPopupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Offer pop-ups: one public read for the storefront and admin management. */
@RestController
public class PromoPopupController {
    @Autowired private PromoPopupService popups;

    /** Public (covered by the GET /api/store/** rule). 204 when nothing is live. */
    @GetMapping("/api/store/popup")
    public ResponseEntity<PromoPopupService.Live> live() {
        return popups.live().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/admin/popups")
    public List<PromoPopup> list() { return popups.list(); }

    @PostMapping("/api/admin/popups")
    public PromoPopup create(@RequestBody PromoPopupService.Input in) { return popups.create(in); }

    @PutMapping("/api/admin/popups/{id}")
    public PromoPopup update(@PathVariable Long id, @RequestBody PromoPopupService.Input in) { return popups.update(id, in); }

    @PutMapping("/api/admin/popups/{id}/active")
    public PromoPopup setActive(@PathVariable Long id, @RequestParam boolean value) { return popups.setActive(id, value); }

    @DeleteMapping("/api/admin/popups/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        popups.delete(id);
        return ResponseEntity.noContent().build();
    }
}
