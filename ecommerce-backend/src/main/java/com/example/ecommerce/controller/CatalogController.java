package com.example.ecommerce.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.ecommerce.dto.AdminProductView;
import com.example.ecommerce.service.CatalogService;
import com.example.ecommerce.service.ProductService;

/** Colours on product pages, the owner's catalogue tools, and the feeds other platforms read. */
@RestController
public class CatalogController {
    @Autowired private CatalogService catalog;
    @Autowired private ProductService productService;

    // ------------------------------------------------------------ public

    @GetMapping("/api/products/{id}/colours")
    public ResponseEntity<List<CatalogService.Colour>> colours(@PathVariable Long id) {
        return ResponseEntity.ok(catalog.coloursOf(productService.getProductById(id)));
    }

    /** Google Merchant Center / Meta Commerce Manager can fetch this URL on a schedule. */
    @GetMapping("/api/store/feed/google.xml")
    public ResponseEntity<String> googleFeed() {
        String apiBase = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/api";
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(15, TimeUnit.MINUTES).cachePublic())
                .body(catalog.googleFeed(apiBase));
    }

    @GetMapping("/api/store/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(catalog.sitemap());
    }

    // ------------------------------------------------------------ admin

    @PostMapping("/api/admin/products/{id}/colour")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminProductView> addColour(@PathVariable Long id, @RequestParam String colour) {
        return ResponseEntity.ok(productService.adminView(catalog.addColour(id, colour)));
    }

    @GetMapping("/api/admin/catalog/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> export() {
        return csv("infinitystore-catalogue.csv", catalog.exportCsv());
    }

    @GetMapping("/api/admin/catalog/template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> template() {
        return csv("infinitystore-import-template.csv", catalog.template());
    }

    @PostMapping(value = "/api/admin/catalog/import", consumes = {"text/csv", "text/plain", "application/octet-stream"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CatalogService.ImportReport> importCsv(@RequestBody byte[] body, @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(catalog.importCsv(new String(body, StandardCharsets.UTF_8), dryRun));
    }

    private static ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body("﻿" + body);
    }
}
