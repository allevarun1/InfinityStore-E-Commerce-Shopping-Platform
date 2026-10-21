package com.example.ecommerce.service;

import com.example.ecommerce.model.MediaAsset;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.MediaAssetRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.StoreVideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
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
    private static final Pattern URL = Pattern.compile("^/api/media/(\\d{1,12})/([a-f0-9]{10})$");

    public record Info(Long id, String name, String contentType, MediaAsset.Kind kind, long sizeBytes, String url, LocalDateTime createdAt) {}

    @Autowired private MediaAssetRepository media;
    @Autowired private ProductRepository products;
    @Autowired private StoreVideoRepository videos;
    @Autowired private AuditService audit;

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
        a.setData(bytes);
        MediaAsset saved = media.save(a);
        audit.record("MEDIA_UPLOADED", "MediaAsset", saved.getId(), saved.getName() + " (" + saved.getContentType() + ", " + saved.getSizeBytes() + " bytes)");
        return info(saved);
    }

    @Transactional(readOnly = true)
    public List<Info> list() {
        return media.findAllByOrderByIdDesc().stream()
                .map(m -> new Info(m.getId(), m.getName(), m.getContentType(), m.getKind(), m.getSizeBytes(), url(m.getId(), m.getChecksum()), m.getCreatedAt()))
                .toList();
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
        media.delete(a);
        audit.record("MEDIA_DELETED", "MediaAsset", id, a.getName());
    }

    private Info info(MediaAsset a) {
        return new Info(a.getId(), a.getName(), a.getContentType(), a.getKind(), a.getSizeBytes(), url(a.getId(), a.getChecksum()), a.getCreatedAt());
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
