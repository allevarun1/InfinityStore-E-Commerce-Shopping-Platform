package com.example.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.service.studio.StudioRequest;
import com.example.ecommerce.service.studio.StudioService;

import jakarta.validation.Valid;

/** Product Studio. Suggestions only: nothing is saved or published from here. */
@RestController
@RequestMapping("/api/admin/studio")
@PreAuthorize("hasRole('ADMIN')")
public class StudioController {
    @Autowired private StudioService studio;

    @GetMapping("/status")
    public ResponseEntity<StudioService.Status> status() {
        return ResponseEntity.ok(studio.status());
    }

    @PostMapping("/draft")
    public ResponseEntity<StudioService.Draft> draft(@Valid @RequestBody StudioRequest request) {
        return ResponseEntity.ok(studio.generate(request));
    }
}
