package com.example.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.service.assistant.ShopAssistantService;

/** The storefront shopping assistant. Public and rate-limited per client address. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    @Autowired private ShopAssistantService assistant;

    @PostMapping("/chat")
    public ResponseEntity<ShopAssistantService.Reply> chat(@RequestBody ShopAssistantService.Request request) {
        return ResponseEntity.ok(assistant.chat(request));
    }
}
