package com.example.ecommerce.controller;

import com.example.ecommerce.service.MediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** The owner's photo and video library (admin) and its public, long-cached files. */
@RestController
public class MediaController {
    @Autowired private MediaService media;

    @PostMapping(value = "/api/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaService.Info upload(@RequestParam("file") MultipartFile file) throws IOException {
        return media.store(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/api/admin/media")
    public List<MediaService.Info> list() { return media.list(); }

    @DeleteMapping("/api/admin/media/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        media.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Public. The checksum is part of the path, so a cached copy is always the right one and may be kept for a year. */
    @GetMapping("/api/media/{id}/{checksum}")
    public ResponseEntity<Resource> serve(@PathVariable Long id, @PathVariable String checksum) {
        return media.find(id, checksum)
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(a.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Disposition", "inline")
                        .header("Cross-Origin-Resource-Policy", "cross-origin")
                        .body((Resource) new ByteArrayResource(a.getData())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
