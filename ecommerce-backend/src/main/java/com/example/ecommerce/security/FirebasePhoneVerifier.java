package com.example.ecommerce.security;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.ecommerce.service.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.UnsupportedJwtException;

/**
 * Checks the ID token the storefront gets from Firebase after a shopper types the OTP sent to their mobile.
 * The token is an RS256 JWT signed by Google; we verify the signature against Google's published certificates
 * and that it was issued for this Firebase project, then trust only its phone_number claim. Off unless
 * FIREBASE_PROJECT_ID is set. No Firebase secret is needed on the server.
 */
@Component
public class FirebasePhoneVerifier {
    static final String CERTS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final String projectId;
    private final String apiKey;
    private final String authDomain;
    private final RestClient client;

    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant keysExpireAt = Instant.EPOCH;

    public FirebasePhoneVerifier(String projectId, String apiKey, String authDomain) {
        this(projectId, apiKey, authDomain, true);
    }

    /** switchedOn lets the shop hide OTP login (PHONE_LOGIN_ENABLED=false) while keeping the Firebase settings in place. */
    @Autowired
    public FirebasePhoneVerifier(
            @Value("${app.firebase.project-id:}") String projectId,
            @Value("${app.firebase.api-key:}") String apiKey,
            @Value("${app.firebase.auth-domain:}") String authDomain,
            @Value("${app.firebase.phone-login-enabled:false}") boolean switchedOn) {
        this.projectId = projectId == null || !switchedOn ? "" : projectId.trim();
        this.apiKey = apiKey == null || !switchedOn ? "" : apiKey.trim();
        this.authDomain = authDomain == null || authDomain.isBlank() ? this.projectId + ".firebaseapp.com" : authDomain.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /** Public web settings the storefront needs to start Firebase; these are not secrets. */
    public record WebConfig(String apiKey, String authDomain, String projectId) {
    }

    public boolean enabled() {
        return !projectId.isEmpty() && !apiKey.isEmpty();
    }

    public WebConfig webConfig() {
        return enabled() ? new WebConfig(apiKey, authDomain, projectId) : null;
    }

    /** The verified mobile number in international form, e.g. +919876543210. */
    public String verify(String idToken) {
        if (!enabled()) throw new BusinessException("Login with mobile number is not available yet");
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setAllowedClockSkewSeconds(60)
                    .requireIssuer("https://securetoken.google.com/" + projectId)
                    .requireAudience(projectId)
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override
                        public Key resolveSigningKey(@SuppressWarnings("rawtypes") JwsHeader header, Claims body) {
                            if (!"RS256".equals(header.getAlgorithm())) throw new UnsupportedJwtException("Unexpected algorithm");
                            return key(header.getKeyId());
                        }
                    })
                    .build()
                    .parseClaimsJws(idToken)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException("Your OTP has expired. Please request a new one.");
        }
        String phone = claims.get("phone_number", String.class);
        if (claims.getSubject() == null || claims.getSubject().isBlank() || phone == null || !E164.matcher(phone).matches()) {
            throw new BusinessException("Please sign in with the OTP sent to your mobile number");
        }
        return phone;
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
        if (Instant.now().isBefore(keysExpireAt.minusSeconds(3600 * 5))) return; // another thread just refreshed
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
            throw new BusinessException("Could not check your OTP right now. Please try again in a moment.");
        }
    }
}
