package com.example.ecommerce.service;

import com.example.ecommerce.model.MediaAsset;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.MediaAssetRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.StoreVideoRepository;
import com.example.ecommerce.service.storage.R2Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The owner's photo and video library. What a file is comes from its first bytes, never from the name or the type the
 * browser claims, and only raster images and mp4/webm video are accepted (never SVG or HTML, which can carry script).
 */
@Service
public class MediaService {
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 20L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Pattern URL = Pattern.compile("^/api/media/(\\d{1,12})/([a-f0-9]{10})$");

    /** usedBy names the saree, video or pop-up that shows the file; null when nothing uses it. */
    public record Info(Long id, String name, String contentType, MediaAsset.Kind kind, long sizeBytes, String url, LocalDateTime createdAt, String usedBy) {}

    /** What "remove unused" did. */
    public record Cleanup(int removed, long bytesFreed) {}

    @Autowired private MediaAssetRepository media;
    @Autowired private ProductRepository products;
    @Autowired private StoreVideoRepository videos;
    @Autowired private com.example.ecommerce.repository.PromoPopupRepository popups;
    @Autowired private AuditService audit;
    @Autowired private R2Storage r2;
    @Autowired private org.springframework.transaction.support.TransactionTemplate tx;

    static String url(Long id, String checksum) { return "/api/media/" + id + "/" + checksum; }

    /** The id inside a library link, or empty when the text is not one. */
    public static Optional<Long> idOf(String url) {
        if (url == null) return Optional.empty();
        var m = URL.matcher(url);
        return m.matches() ? Optional.of(Long.valueOf(m.group(1))) : Optional.empty();
    }

    /** True when the link points at a stored file of the given kind whose checksum matches. */
    @Transactional(readOnly = true)
    public boolean isLibraryLink(String url, MediaAsset.Kind kind) {
        var m = URL.matcher(url == null ? "" : url);
        if (!m.matches()) return false;
        return media.findById(Long.valueOf(m.group(1)))
                .filter(a -> a.getChecksum().equals(m.group(2)) && (kind == null || a.getKind() == kind)).isPresent();
    }

    @Transactional
    public Info store(String originalName, byte[] bytes) {
        if (bytes == null || bytes.length < 12) throw new BusinessException("That file is empty");
        String type = sniff(bytes);
        if (type == null) throw new BusinessException("Use a JPEG, PNG, WebP or GIF photo, or an MP4 or WebM video");
        MediaAsset.Kind kind = type.startsWith("video/") ? MediaAsset.Kind.VIDEO : MediaAsset.Kind.IMAGE;
        long max = kind == MediaAsset.Kind.VIDEO ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (bytes.length > max) {
            throw new BusinessException((kind == MediaAsset.Kind.VIDEO ? "Videos" : "Photos") + " can be up to " + (max / 1024 / 1024) + " MB");
        }
        MediaAsset a = new MediaAsset();
        a.setName(cleanName(originalName));
        a.setContentType(type);
        a.setKind(kind);
        a.setSizeBytes(bytes.length);
        a.setChecksum(checksum(bytes));
        if (r2.enabled()) {
            // File goes to R2 first; the database row is only written once the file is safely stored.
            String key = keyFor(a.getChecksum(), a.getName());
            try {
                r2.put(key, bytes, type);
            } catch (RuntimeException e) {
                throw new BusinessException("The file could not be saved right now. Please try again.");
            }
            a.setStorageKey(key);
        } else {
            a.setData(bytes);
        }
        MediaAsset saved = media.save(a);
        audit.record("MEDIA_UPLOADED", "MediaAsset", saved.getId(), saved.getName() + " (" + saved.getContentType() + ", " + saved.getSizeBytes() + " bytes)");
        return info(saved);
    }

    @Transactional(readOnly = true)
    public List<Info> list() {
        java.util.Map<String, String> uses = uses();
        return media.findAllByOrderByIdDesc().stream()
                .map(m -> {
                    String link = url(m.getId(), m.getChecksum());
                    return new Info(m.getId(), m.getName(), m.getContentType(), m.getKind(), m.getSizeBytes(), link, m.getCreatedAt(), uses.get(link));
                })
                .toList();
    }

    /** Every library link shown somewhere on the shop, mapped to a readable name of where it is used. */
    private java.util.Map<String, String> uses() {
        java.util.Map<String, String> uses = new java.util.HashMap<>();
        for (Product p : products.findAll()) {
            if (p.getImageUrl() != null) uses.putIfAbsent(p.getImageUrl(), "Saree: " + p.getName());
            for (String g : p.getGalleryRaw()) uses.putIfAbsent(g, "Saree: " + p.getName());
        }
        for (var v : videos.findAll()) {
            if (v.getVideoUrl() != null) uses.putIfAbsent(v.getVideoUrl(), "Video: " + v.getTitle());
            if (v.getPosterUrl() != null) uses.putIfAbsent(v.getPosterUrl(), "Video: " + v.getTitle());
        }
        for (var pp : popups.findAll()) {
            if (pp.getImageUrl() != null) uses.putIfAbsent(pp.getImageUrl(), "Pop-up: " + pp.getName());
        }
        return uses;
    }

    /**
     * Removes every file no saree, video or pop-up uses. Files uploaded in the last hour are kept, so a photo
     * uploaded while a saree is still being edited (not saved yet) is never lost.
     */
    public Cleanup deleteUnused() {
        java.util.Map<String, String> uses = tx.execute(s -> uses());
        LocalDateTime recent = LocalDateTime.now().minusHours(1);
        int removed = 0;
        long freed = 0;
        for (MediaAssetRepository.Meta a : media.findAllByOrderByIdDesc()) {
            if (uses.containsKey(url(a.getId(), a.getChecksum()))) continue;
            if (a.getCreatedAt() == null || a.getCreatedAt().isAfter(recent)) continue;
            try {
                Long id = a.getId();
                tx.executeWithoutResult(t -> delete(id));
                removed++;
                freed += a.getSizeBytes();
            } catch (RuntimeException e) {
                log.warn("Could not remove unused media {}: {}", a.getId(), e.getMessage());
            }
        }
        audit.record("MEDIA_CLEANUP", "MediaAsset", null, removed + " unused file(s), " + freed + " bytes freed");
        return new Cleanup(removed, freed);
    }

    /** The file for a public link, only when the checksum part matches what is stored. */
    @Transactional(readOnly = true)
    public Optional<MediaAsset> find(Long id, String checksum) {
        return media.findById(id).filter(a -> a.getChecksum().equals(checksum));
    }

    @Transactional
    public void delete(Long id) {
        MediaAsset a = media.findById(id).orElseThrow(() -> new BusinessException("File not found"));
        String link = url(a.getId(), a.getChecksum());
        for (Product p : products.findAll()) {
            if (link.equals(p.getImageUrl()) || p.getGalleryRaw().contains(link)) {
                throw new BusinessException("This file is used by the saree \"" + p.getName() + "\". Change its photos first.");
            }
        }
        for (var v : videos.findAll()) {
            if (link.equals(v.getVideoUrl()) || link.equals(v.getPosterUrl())) {
                throw new BusinessException("This file is used by the video \"" + v.getTitle() + "\". Remove that video first.");
            }
        }
        for (var pp : popups.findAll()) {
            if (link.equals(pp.getImageUrl())) {
                throw new BusinessException("This file is used by the pop-up \"" + pp.getName() + "\". Change that pop-up first.");
            }
        }
        media.delete(a);
        audit.record("MEDIA_DELETED", "MediaAsset", id, a.getName());
        String key = a.getStorageKey();
        if (key != null && r2.enabled()) {
            afterCommit(() -> {
                try {
                    r2.delete(key);
                } catch (RuntimeException e) {
                    log.warn("Could not remove {} from R2: {}", key, e.getMessage());
                }
            });
        }
    }

    /** Public address of a stored file in R2, or null when its bytes are served from the database. */
    public String publicUrlOf(MediaAsset a) {
        return a.getStorageKey() != null && r2.enabled() ? r2.publicUrl(a.getStorageKey()) : null;
    }

    /**
     * Moves files uploaded before R2 was set up out of the database, one at a time, so the 1 GB database is freed.
     * Links on the website keep working: /api/media/... now points to the R2 copy.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void moveDatabaseFilesToR2OnStartup() {
        moveDatabaseFilesToR2();
    }

    public void moveDatabaseFilesToR2() {
        if (!r2.enabled()) return;
        List<Long> ids = media.findIdsStoredInDatabase();
        if (ids.isEmpty()) return;
        log.info("Moving {} uploaded file(s) from the database to R2", ids.size());
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> media.findById(id).ifPresent(a -> {
                    if (a.getStorageKey() != null || a.getData() == null) return;
                    String key = keyFor(a.getChecksum(), a.getName());
                    r2.put(key, a.getData(), a.getContentType());
                    a.setStorageKey(key);
                    a.setData(null);
                    media.save(a);
                }));
            } catch (RuntimeException e) {
                log.warn("Could not move media {} to R2 yet: {}", id, e.getMessage());
            }
        }
    }

    static String keyFor(String checksum, String name) {
        String safe = name.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        return "media/" + LocalDate.now().getYear() + "/" + checksum + "-" + safe;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private Info info(MediaAsset a) {
        return new Info(a.getId(), a.getName(), a.getContentType(), a.getKind(), a.getSizeBytes(), url(a.getId(), a.getChecksum()), a.getCreatedAt(), null);
    }

    /** JPEG, PNG, GIF, WebP, MP4 or WebM by magic number; null for anything else. */
    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "video/mp4";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) return "video/webm";
        return null;
    }

    private static String cleanName(String n) {
        String base = n == null ? "upload" : n.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "").trim();
        if (base.isEmpty()) base = "upload";
        return base.length() > 80 ? base.substring(base.length() - 80) : base;
    }

    private static String checksum(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder s = new StringBuilder();
            for (byte x : d) s.append(String.format("%02x", x));
            return s.substring(0, 10);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
