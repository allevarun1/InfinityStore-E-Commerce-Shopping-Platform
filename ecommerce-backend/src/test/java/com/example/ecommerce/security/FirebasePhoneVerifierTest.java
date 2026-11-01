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

class FirebasePhoneVerifierTest {
    private static final String PROJECT = "rekha-test";
    private final KeyPair google = Keys.keyPairFor(SignatureAlgorithm.RS256);
    private final KeyPair stranger = Keys.keyPairFor(SignatureAlgorithm.RS256);
    private FirebasePhoneVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new FirebasePhoneVerifier(PROJECT, "public-web-key", "");
        verifier.useKeys(Map.of("k1", google.getPublic()));
    }

    private io.jsonwebtoken.JwtBuilder token() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setHeaderParam("kid", "k1")
                .setIssuer("https://securetoken.google.com/" + PROJECT)
                .setAudience(PROJECT)
                .setSubject("firebase-uid-1")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 3_600_000))
                .claim("phone_number", "+919876543210");
    }

    @Test
    void acceptsAGoogleSignedTokenForThisProjectAndReturnsTheNumber() {
        assertEquals("+919876543210", verifier.verify(token().signWith(google.getPrivate(), SignatureAlgorithm.RS256).compact()));
    }

    @Test
    void rejectsTokensNotSignedByGoogle() {
        String forged = token().signWith(stranger.getPrivate(), SignatureAlgorithm.RS256).compact();
        assertThrows(BusinessException.class, () -> verifier.verify(forged));
    }

    @Test
    void rejectsTokensForAnotherProject() {
        String other = token().setAudience("someone-else").signWith(google.getPrivate(), SignatureAlgorithm.RS256).compact();
        assertThrows(BusinessException.class, () -> verifier.verify(other));
        String wrongIssuer = token().setIssuer("https://securetoken.google.com/someone-else")
                .signWith(google.getPrivate(), SignatureAlgorithm.RS256).compact();
        assertThrows(BusinessException.class, () -> verifier.verify(wrongIssuer));
    }

    @Test
    void rejectsExpiredTokensAndTokensWithoutAPhoneNumber() {
        long past = System.currentTimeMillis() - 7_200_000;
        String expired = token().setIssuedAt(new Date(past)).setExpiration(new Date(past + 60_000))
                .signWith(google.getPrivate(), SignatureAlgorithm.RS256).compact();
        assertThrows(BusinessException.class, () -> verifier.verify(expired));
        String emailOnly = token().claim("phone_number", null).signWith(google.getPrivate(), SignatureAlgorithm.RS256).compact();
        assertThrows(BusinessException.class, () -> verifier.verify(emailOnly));
    }

    @Test
    void rejectsUnsignedAndSymmetricTokens() {
        String unsigned = token().compact();
        assertThrows(BusinessException.class, () -> verifier.verify(unsigned));
        String hmac = token().signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256)).compact();
        assertThrows(BusinessException.class, () -> verifier.verify(hmac));
    }

    @Test
    void isOffUntilConfigured() {
        FirebasePhoneVerifier off = new FirebasePhoneVerifier("", "", "");
        assertFalse(off.enabled());
        assertNull(off.webConfig());
        assertThrows(BusinessException.class, () -> off.verify("anything"));
        assertEquals("rekha-test.firebaseapp.com", verifier.webConfig().authDomain());
    }
}
