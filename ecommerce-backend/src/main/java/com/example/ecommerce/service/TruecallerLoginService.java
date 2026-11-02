package com.example.ecommerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Truecaller one-tap sign-in for the mobile website (Android phones with the Truecaller app).
 * 1. The storefront asks for a nonce and opens the Truecaller app with it.
 * 2. The shopper taps Continue; Truecaller's servers POST {requestId, accessToken, endpoint} to our callback.
 * 3. We fetch the verified profile from that endpoint (only https://*.truecaller.com is accepted, so a forged
 *    callback cannot point us anywhere else, and a forged token simply fails at Truecaller).
 * 4. The storefront polls with its nonce and receives the session once the profile is in.
 * Pending requests live in memory for two minutes; nothing about them needs to survive a restart.
 */
@Service
public class TruecallerLoginService {
    private static final Logger log = LoggerFactory.getLogger(TruecallerLoginService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int MAX_PENDING = 5000;

    private final String appKey;
    private final String appName;
    private final RestClient client;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public TruecallerLoginService(@Value("${app.truecaller.app-key:}") String appKey,
                                  @Value("${app.truecaller.app-name:Rekha Shopping Mall}") String appName) {
        this.appKey = appKey == null ? "" : appKey.trim();
        this.appName = appName == null || appName.isBlank() ? "Rekha Shopping Mall" : appName.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /** What the storefront needs to open the Truecaller app; null when Truecaller is not set up. */
    public record WebConfig(String appKey, String appName) {
    }

    public enum State { PENDING, VERIFIED, REJECTED }

    /** A verified Truecaller profile: E.164 phone plus whatever name/email the shopper has in Truecaller. */
    public record Verified(String phone, String firstName, String lastName, String email) {
    }

    private record Pending(Instant createdAt, State state, Verified profile) {
    }

    public WebConfig webConfig() {
        return appKey.isEmpty() ? null : new WebConfig(appKey, appName);
    }

    public String start() {
        if (appKey.isEmpty()) throw new BusinessException("Truecaller sign-in is not available");
        prune();
        if (pending.size() >= MAX_PENDING) throw new BusinessException("Please try again in a moment");
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        String nonce = HexFormat.of().formatHex(b);
        pending.put(nonce, new Pending(Instant.now(), State.PENDING, null));
        return nonce;
    }

    /** Called by Truecaller's servers. Unknown or expired request IDs are ignored. */
    public void callback(JsonNode body) {
        String nonce = body.path("requestId").asText("");
        Pending p = pending.get(nonce);
        if (p == null || p.state() != State.PENDING || expired(p)) return;
        if (body.hasNonNull("status") && !body.hasNonNull("accessToken")) {
            // user_rejected / use_another_number: the shopper chose another way to sign in
            pending.put(nonce, new Pending(p.createdAt(), State.REJECTED, null));
            return;
        }
        String token = body.path("accessToken").asText("");
        String endpoint = body.path("endpoint").asText("");
        if (token.isEmpty() || !trustedEndpoint(endpoint)) {
            log.warn("Ignored a Truecaller callback with a missing token or untrusted endpoint");
            return;
        }
        try {
            JsonNode profile = client.get().uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + token)
                    .header("Cache-Control", "no-cache")
                    .retrieve().body(JsonNode.class);
            String digits = profile.path("phoneNumbers").path(0).asText("").replaceAll("\\D", "");
            if (!digits.matches("[1-9]\\d{7,14}")) {
                log.warn("Truecaller profile without a usable phone number");
                return;
            }
            JsonNode name = profile.path("name");
            pending.put(nonce, new Pending(p.createdAt(), State.VERIFIED, new Verified("+" + digits,
                    text(name.path("first")), text(name.path("last")), text(profile.path("onlineIdentities").path("email")))));
        } catch (RuntimeException e) {
            log.warn("Could not fetch the Truecaller profile: {}", e.getMessage());
        }
    }

    /** The current state; a verified profile is handed out once and then forgotten. */
    public Map.Entry<State, Verified> poll(String nonce) {
        Pending p = nonce == null ? null : pending.get(nonce);
        if (p == null || expired(p)) {
            if (p != null) pending.remove(nonce);
            throw new BusinessException("This Truecaller request has expired. Please try again.");
        }
        if (p.state() == State.PENDING) return Map.entry(State.PENDING, NONE);
        pending.remove(nonce);
        return Map.entry(p.state(), p.profile() == null ? NONE : p.profile());
    }

    private static final Verified NONE = new Verified("", null, null, null);

    static boolean trustedEndpoint(String endpoint) {
        try {
            URI u = URI.create(endpoint);
            String host = u.getHost();
            return "https".equals(u.getScheme()) && host != null && u.getUserInfo() == null && u.getPort() == -1
                    && (host.equals("truecaller.com") || host.endsWith(".truecaller.com"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String text(JsonNode n) {
        String s = n.isMissingNode() || n.isNull() ? null : n.asText().trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private static boolean expired(Pending p) {
        return p.createdAt().plus(TTL).isBefore(Instant.now());
    }

    private void prune() {
        pending.entrySet().removeIf(e -> expired(e.getValue()));
    }

    /** Tests: pretend Truecaller verified this request. */
    public void markVerified(String nonce, Verified profile) {
        pending.computeIfPresent(nonce, (k, p) -> new Pending(p.createdAt(), State.VERIFIED, profile));
    }
}
