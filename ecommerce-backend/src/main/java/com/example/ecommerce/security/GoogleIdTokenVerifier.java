package com.example.ecommerce.security;

import com.example.ecommerce.service.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.UnsupportedJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Continue with Google": checks the ID token Google Identity Services gives the storefront. The RS256 signature is
 * verified against Google's published certificates (cached, and fetched once at startup so the first login is fast),
 * and the token must be for this site's client ID with a verified email. Off unless GOOGLE_CLIENT_ID is set.
 */
@Component
public class GoogleIdTokenVerifier {
    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);
    static final String CERTS_URL = "https://www.googleapis.com/oauth2/v1/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private final String clientId;
    private final RestClient client;
    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant keysExpireAt = Instant.EPOCH;

    public GoogleIdTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public record GoogleUser(String subject, String email, String firstName, String lastName) {
    }

    public boolean enabled() {
        return !clientId.isEmpty();
    }

    /** Public: the storefront needs it to show the Google button. */
    public String clientId() {
        return enabled() ? clientId : null;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled()) return;
        try {
            refreshKeys();
        } catch (RuntimeException e) {
            log.warn("Could not prefetch Google certificates: {}", e.getMessage());
        }
    }

    public GoogleUser verify(String credential) {
        if (!enabled()) throw new BusinessException("Sign in with Google is not available yet");
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setAllowedClockSkewSeconds(60)
                    .requireAudience(clientId)
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override
                        public Key resolveSigningKey(@SuppressWarnings("rawtypes") JwsHeader header, Claims body) {
                            if (!"RS256".equals(header.getAlgorithm())) throw new UnsupportedJwtException("Unexpected algorithm");
                            return key(header.getKeyId());
                        }
                    })
                    .build()
                    .parseClaimsJws(credential)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException("Google sign-in did not complete. Please try again.");
        }
        Object verified = claims.get("email_verified");
        String email = claims.get("email", String.class);
        if (!ISSUERS.contains(claims.getIssuer()) || claims.getSubject() == null || claims.getSubject().isBlank()
                || email == null || !(Boolean.TRUE.equals(verified) || "true".equals(verified))) {
            throw new BusinessException("Please use a Google account with a verified email");
        }
        return new GoogleUser(claims.getSubject(), email.trim().toLowerCase(),
                claims.get("given_name", String.class), claims.get("family_name", String.class));
    }

    /** Tests: trust these keys for an hour instead of fetching Google's certificates. */
    void useKeys(Map<String, PublicKey> testKeys) {
        keys = Map.copyOf(testKeys);
        keysExpireAt = Instant.now().plusSeconds(3600);
    }

    private PublicKey key(String kid) {
        if (kid == null) throw new UnsupportedJwtException("Missing key id");
        PublicKey k = Instant.now().isBefore(keysExpireAt) ? keys.get(kid) : null;
        if (k == null) {
            refreshKeys();
            k = keys.get(kid);
        }
        if (k == null) throw new UnsupportedJwtException("Unknown key id");
        return k;
    }

    private synchronized void refreshKeys() {
        if (Instant.now().isBefore(keysExpireAt.minusSeconds(3600))) return; // another thread just refreshed
        try {
            ResponseEntity<JsonNode> res = client.get().uri(CERTS_URL).retrieve().toEntity(JsonNode.class);
            Map<String, PublicKey> fresh = new HashMap<>();
            CertificateFactory x509 = CertificateFactory.getInstance("X.509");
            res.getBody().fields().forEachRemaining(e -> {
                try {
                    byte[] pem = e.getValue().asText().getBytes(StandardCharsets.UTF_8);
                    fresh.put(e.getKey(), x509.generateCertificate(new ByteArrayInputStream(pem)).getPublicKey());
                } catch (Exception ignored) {
                    // skip a certificate we cannot read; the others still work
                }
            });
            String cacheControl = res.getHeaders().getCacheControl();
            Matcher m = MAX_AGE.matcher(cacheControl == null ? "" : cacheControl);
            long maxAge = m.find() ? Long.parseLong(m.group(1)) : 3600;
            keys = Map.copyOf(fresh);
            keysExpireAt = Instant.now().plusSeconds(maxAge);
        } catch (Exception e) {
            throw new BusinessException("Could not reach Google right now. Please try again in a moment.");
        }
    }
}
