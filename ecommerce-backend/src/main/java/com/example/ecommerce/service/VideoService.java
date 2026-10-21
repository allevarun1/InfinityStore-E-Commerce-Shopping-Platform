package com.example.ecommerce.service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.StoreVideo;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.StoreVideoRepository;

/** Owner-managed storefront videos. Only links are stored, and only safe https links are accepted. */
@Service
public class VideoService {
    private static final Set<String> FILE_TYPES = Set.of(".mp4", ".webm", ".mov", ".m4v");
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com", "youtube-nocookie.com");
    private static final Pattern YT_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern YT_PATH = Pattern.compile("^/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})(?:/.*)?$");
    private static final Pattern YT_QUERY = Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)");

    @Autowired private StoreVideoRepository videos;
    @Autowired private ProductRepository products;
    @Autowired private AuditService audit;
    @Autowired private MediaService media;

    public record Input(String title, String caption, StoreVideo.Kind kind, String videoUrl, String posterUrl, Long productId, Integer sortOrder) {}

    /** What the storefront needs to play one video. youtubeId is set instead of a file for YouTube links. */
    public record View(Long id, String title, String caption, StoreVideo.Kind kind, String videoUrl, String youtubeId,
                       String posterUrl, Long productId, String productName, boolean active, int sortOrder) {}

    @Transactional(readOnly = true)
    public List<View> publicList(Long productId) {
        return videos.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .filter(v -> productId == null || productId.equals(v.getProductId()))
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<View> all() {
        return videos.findAllByOrderBySortOrderAscIdAsc().stream().map(this::view).toList();
    }

    @Transactional
    public View create(Input in) {
        StoreVideo v = new StoreVideo();
        apply(v, in);
        v.setSortOrder(in.sortOrder() == null ? (int) videos.count() + 1 : in.sortOrder());
        StoreVideo saved = videos.save(v);
        audit.record("VIDEO_ADDED", "StoreVideo", saved.getId(), saved.getTitle());
        return view(saved);
    }

    @Transactional
    public View setActive(Long id, boolean active) {
        StoreVideo v = find(id);
        v.setActive(active);
        audit.record("VIDEO_ACTIVE_CHANGED", "StoreVideo", id, v.getTitle() + " active=" + active);
        return view(videos.save(v));
    }

    @Transactional
    public void delete(Long id) {
        StoreVideo v = find(id);
        videos.delete(v);
        audit.record("VIDEO_DELETED", "StoreVideo", id, v.getTitle());
    }

    private StoreVideo find(Long id) {
        return videos.findById(id).orElseThrow(() -> new BusinessException("Video not found"));
    }

    private void apply(StoreVideo v, Input in) {
        String title = in.title() == null ? "" : in.title().trim();
        if (title.isEmpty() || title.length() > 120) throw new BusinessException("Give the video a short title (up to 120 characters)");
        String caption = in.caption() == null ? null : in.caption().trim();
        if (caption != null && caption.length() > 240) throw new BusinessException("Keep the caption under 240 characters");
        String url = in.videoUrl() == null ? "" : in.videoUrl().trim();
        if (!isFile(url) && !isSiteFile(url) && youtubeId(url).isEmpty() && !media.isLibraryLink(url, com.example.ecommerce.model.MediaAsset.Kind.VIDEO)) {
            throw new BusinessException("Use a direct https link to an .mp4 or .webm file, a file under /videos on this site, a video from your library, or a YouTube link");
        }
        String poster = in.posterUrl() == null || in.posterUrl().isBlank() ? null : in.posterUrl().trim();
        if (poster != null && !(poster.startsWith("/") && !poster.startsWith("//")) && !isHttps(poster)) {
            throw new BusinessException("The cover image must be an https link");
        }
        if (in.productId() != null && !products.existsById(in.productId())) throw new BusinessException("That saree does not exist");
        v.setTitle(title);
        v.setCaption(caption == null || caption.isEmpty() ? null : caption);
        v.setKind(in.kind() == null ? StoreVideo.Kind.REEL : in.kind());
        v.setVideoUrl(url);
        v.setPosterUrl(poster);
        v.setProductId(in.productId());
    }

    static boolean isHttps(String url) {
        if (url == null || url.length() > 500) return false;
        try {
            URI u = new URI(url);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && u.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** A clip served by the storefront itself, e.g. /videos/loom.mp4 (dropped into the site's public folder). */
    static boolean isSiteFile(String url) {
        if (url == null || url.length() > 200 || !url.matches("^/videos/[A-Za-z0-9._/-]+$") || url.contains("..") || url.contains("//")) return false;
        String p = url.toLowerCase(Locale.ROOT);
        return FILE_TYPES.stream().anyMatch(p::endsWith);
    }

    static boolean isFile(String url) {
        if (!isHttps(url)) return false;
        String path = URI.create(url).getPath();
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        return FILE_TYPES.stream().anyMatch(p::endsWith);
    }

    /** The 11-character video id from a watch, short, embed or Shorts link, when the host really is YouTube. */
    static Optional<String> youtubeId(String url) {
        if (!isHttps(url)) return Optional.empty();
        URI u = URI.create(url);
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (!YOUTUBE_HOSTS.contains(host)) return Optional.empty();
        if (host.equals("youtu.be")) {
            String id = u.getPath() == null ? "" : u.getPath().replaceFirst("^/", "");
            return YT_ID.matcher(id).matches() ? Optional.of(id) : Optional.empty();
        }
        Matcher p = YT_PATH.matcher(u.getPath() == null ? "" : u.getPath());
        if (p.matches()) return Optional.of(p.group(1));
        Matcher q = YT_QUERY.matcher(u.getRawQuery() == null ? "" : u.getRawQuery());
        return q.find() ? Optional.of(q.group(1)) : Optional.empty();
    }

    private View view(StoreVideo v) {
        String yt = youtubeId(v.getVideoUrl()).orElse(null);
        String name = null;
        Long productId = null;
        if (v.getProductId() != null) {
            Optional<Product> p = products.findById(v.getProductId());
            if (p.isPresent() && p.get().isPublished()) { productId = p.get().getId(); name = p.get().getName(); }
        }
        return new View(v.getId(), v.getTitle(), v.getCaption(), v.getKind(), yt == null ? v.getVideoUrl() : null, yt,
                v.getPosterUrl(), productId, name, v.isActive(), v.getSortOrder());
    }
}
