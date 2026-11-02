package com.example.ecommerce.security;

import com.example.ecommerce.service.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoogleIdTokenVerifierTest {
    private static final String CLIENT = "123-abc.apps.googleusercontent.com";
    private final KeyPair google = Keys.keyPairFor(SignatureAlgorithm.RS256);
    private final KeyPair stranger = Keys.keyPairFor(SignatureAlgorithm.RS256);
    private GoogleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GoogleIdTokenVerifier(CLIENT);
        verifier.useKeys(Map.of("g1", google.getPublic()));
    }

    private io.jsonwebtoken.JwtBuilder token() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setHeaderParam("kid", "g1")
                .setIssuer("https://accounts.google.com")
                .setAudience(CLIENT)
                .setSubject("google-sub-1")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 3_600_000))
                .claim("email", "Lakshmi.Devi@gmail.com")
                .claim("email_verified", true)
                .claim("given_name", "Lakshmi")
                .claim("family_name", "Devi");
    }

    private String signed(io.jsonwebtoken.JwtBuilder b) {
        return b.signWith(google.getPrivate(), SignatureAlgorithm.RS256).compact();
    }

    @Test
    void acceptsAGoogleSignedTokenForThisSite() {
        GoogleIdTokenVerifier.GoogleUser u = verifier.verify(signed(token()));
        assertEquals("google-sub-1", u.subject());
        assertEquals("lakshmi.devi@gmail.com", u.email());
        assertEquals("Lakshmi", u.firstName());
        assertEquals("Devi", u.lastName());
        assertDoesNotThrow(() -> verifier.verify(signed(token().setIssuer("accounts.google.com"))));
    }

    @Test
    void rejectsForgedOrMisdirectedTokens() {
        assertThrows(BusinessException.class, () -> verifier.verify(token().signWith(stranger.getPrivate(), SignatureAlgorithm.RS256).compact()));
        assertThrows(BusinessException.class, () -> verifier.verify(signed(token().setAudience("another-site"))));
        assertThrows(BusinessException.class, () -> verifier.verify(signed(token().setIssuer("https://evil.example"))));
        assertThrows(BusinessException.class, () -> verifier.verify(signed(token().setExpiration(new Date(System.currentTimeMillis() - 600_000)))));
        assertThrows(BusinessException.class, () -> verifier.verify(signed(token().claim("email_verified", false))));
        assertThrows(BusinessException.class, () -> verifier.verify(
                Jwts.builder().setAudience(CLIENT).setSubject("x").signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256)).compact()));
        assertThrows(BusinessException.class, () -> verifier.verify("not-a-token"));
    }

    @Test
    void offWithoutAClientId() {
        GoogleIdTokenVerifier off = new GoogleIdTokenVerifier("");
        assertFalse(off.enabled());
        assertNull(off.clientId());
        assertThrows(BusinessException.class, () -> off.verify(signed(token())));
    }
}
