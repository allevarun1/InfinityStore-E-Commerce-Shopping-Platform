package com.example.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.model.AutomationEvent;
import com.example.ecommerce.service.AutopilotService;

/** Admin control of Autopilot: rule modes, the to-do list, the message log and "Run now". */
@RestController
@RequestMapping("/api/admin/autopilot")
@PreAuthorize("hasRole('ADMIN')")
public class AutopilotController {
    @Autowired private AutopilotService autopilot;

    @GetMapping
    public ResponseEntity<AutopilotService.Overview> overview() {
        return ResponseEntity.ok(autopilot.overview());
    }

    @PutMapping("/rules/{key}")
    public ResponseEntity<AutopilotService.RuleView> setMode(@PathVariable String key, @RequestParam String mode) {
        return ResponseEntity.ok(autopilot.setMode(key, mode));
    }

    @PostMapping("/run")
    public ResponseEntity<AutopilotService.RunSummary> run() {
        return ResponseEntity.ok(autopilot.runAll());
    }

    @PostMapping("/events/{id}/send")
    public ResponseEntity<AutomationEvent> send(@PathVariable Long id) {
        return ResponseEntity.ok(autopilot.send(id));
    }

    @PostMapping("/events/{id}/close")
    public ResponseEntity<AutomationEvent> close(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean sent) {
        return ResponseEntity.ok(autopilot.close(id, sent));
    }
}
