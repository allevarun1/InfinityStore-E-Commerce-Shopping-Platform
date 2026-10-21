package com.example.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.model.StoreVideo;
import com.example.ecommerce.service.VideoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Storefront videos: a public list for shoppers, management for the owner. */
@RestController
public class VideoController {
    public record VideoRequest(@NotBlank @Size(max = 120) String title, @Size(max = 240) String caption, StoreVideo.Kind kind,
                               @NotBlank @Size(max = 500) String videoUrl, @Size(max = 500) String posterUrl, Long productId, Integer sortOrder) {}

    @Autowired private VideoService videos;

    @GetMapping("/api/store/videos")
    public ResponseEntity<List<VideoService.View>> publicList(@RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(videos.publicList(productId));
    }

    @GetMapping("/api/admin/videos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VideoService.View>> all() {
        return ResponseEntity.ok(videos.all());
    }

    @PostMapping("/api/admin/videos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VideoService.View> create(@Valid @RequestBody VideoRequest r) {
        return ResponseEntity.ok(videos.create(new VideoService.Input(r.title(), r.caption(), r.kind(), r.videoUrl(), r.posterUrl(), r.productId(), r.sortOrder())));
    }

    @PutMapping("/api/admin/videos/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VideoService.View> setActive(@PathVariable Long id, @RequestParam boolean value) {
        return ResponseEntity.ok(videos.setActive(id, value));
    }

    @DeleteMapping("/api/admin/videos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id) {
        videos.delete(id);
        return ResponseEntity.ok(new MessageResponse("Video removed"));
    }
}
