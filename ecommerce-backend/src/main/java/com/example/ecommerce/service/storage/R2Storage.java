package com.example.ecommerce.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cloudflare R2 (S3-compatible) file storage for uploaded photos and videos, so the 1 GB database only keeps saree
 * details. Requests are signed with AWS Signature V4; no SDK needed. Off until all R2_* settings are present, in which
 * case uploads stay in the database as before.
 */
@Component
public class R2Storage {
    private static final Logger log = LoggerFactory.getLogger(R2Storage.class);
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final String accountId;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final String publicUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public R2Storage(@Value("${app.storage.r2.account-id:}") String accountId,
                     @Value("${app.storage.r2.access-key-id:}") String accessKey,
                     @Value("${app.storage.r2.secret-access-key:}") String secretKey,
                     @Value("${app.storage.r2.bucket:}") String bucket,
                     @Value("${app.storage.r2.public-url:}") String publicUrl) {
        this.accountId = accountId.trim();
        this.accessKey = accessKey.trim();
        this.secretKey = secretKey.trim();
        this.bucket = bucket.trim();
        String base = publicUrl.trim();
        this.publicUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public boolean enabled() {
        return !accountId.isEmpty() && !accessKey.isEmpty() && !secretKey.isEmpty() && !bucket.isEmpty() && publicUrl.startsWith("https://");
    }

    /** Where browsers fetch a stored file from. */
    public String publicUrl(String key) {
        return publicUrl + "/" + key;
    }

    public void put(String key, byte[] bytes, String contentType) {
        send("PUT", key, bytes, Map.of("content-type", contentType, "cache-control", "public, max-age=31536000, immutable"));
    }

    public void delete(String key) {
        send("DELETE", key, new byte[0], Map.of());
    }

    private void send(String method, String key, byte[] body, Map<String, String> extra) {
        String host = accountId + ".r2.cloudflarestorage.com";
        String path = "/" + bucket + "/" + key;
        TreeMap<String, String> headers = new TreeMap<>(extra);
        String amzDate = ZonedDateTime.now(ZoneOffset.UTC).format(AMZ_DATE);
        String authorization = sign(method, host, path, headers, body, amzDate, "auto", accessKey, secretKey);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("https://" + host + encodePath(path)))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", authorization)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", hex(sha256(body)))
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        extra.forEach(req::header);
        try {
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2 && !(method.equals("DELETE") && res.statusCode() == 404)) {
                log.error("R2 {} {} failed: {} {}", method, key, res.statusCode(), res.body());
                throw new IllegalStateException("File storage refused the request (" + res.statusCode() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("File storage request interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("File storage is not reachable", e);
        }
    }

    /**
     * AWS Signature V4 Authorization header. `headers` holds lower-case names of extra signed headers; host,
     * x-amz-content-sha256 and x-amz-date are always signed.
     */
    static String sign(String method, String host, String path, Map<String, String> headers, byte[] body,
                       String amzDate, String region, String accessKey, String secretKey) {
        String payloadHash = hex(sha256(body));
        TreeMap<String, String> signed = new TreeMap<>(headers);
        signed.put("host", host);
        signed.put("x-amz-content-sha256", payloadHash);
        signed.put("x-amz-date", amzDate);
        StringBuilder canonicalHeaders = new StringBuilder();
        signed.forEach((k, v) -> canonicalHeaders.append(k).append(':').append(v.trim()).append('\n'));
        String signedHeaders = String.join(";", signed.keySet());
        String canonical = method + "\n" + encodePath(path) + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String date = amzDate.substring(0, 8);
        String scope = date + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
        byte[] k = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        k = hmac(k, region);
        k = hmac(k, "s3");
        k = hmac(k, "aws4_request");
        return "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + hex(hmac(k, toSign));
    }

    /** RFC 3986 encoding of each path segment, keeping the slashes. */
    static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-_.~/".indexOf(c) >= 0) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
