package com.example.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.service.InsightsService;
import com.example.ecommerce.service.OpportunityService;

/** Business intelligence and opportunity scouting. Admin only: contains cost, margin and sales data. */
@RestController
@RequestMapping("/api/admin/insights")
@PreAuthorize("hasRole('ADMIN')")
public class InsightsController {
    @Autowired private InsightsService insights;
    @Autowired private OpportunityService scout;

    @GetMapping("/dashboard")
    public ResponseEntity<InsightsService.Dashboard> dashboard(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(insights.dashboard(days));
    }

    @GetMapping("/opportunities")
    public ResponseEntity<OpportunityService.Report> opportunities() {
        return ResponseEntity.ok(scout.scout());
    }
}
