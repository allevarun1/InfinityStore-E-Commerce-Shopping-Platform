package com.example.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.service.ReferralService;

/** The signed-in customer's referral code and the credits they have earned. */
@RestController
@RequestMapping("/api/referral")
public class ReferralController extends BaseUserController {
    @Autowired private ReferralService referrals;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReferralService.Summary> mine() {
        return ResponseEntity.ok(referrals.summary(currentUser()));
    }
}
