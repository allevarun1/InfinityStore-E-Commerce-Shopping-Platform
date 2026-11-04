package com.example.ecommerce.service.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * The Web Push standard with only the JDK: message encryption (RFC 8291, "aes128gcm") and the VAPID
 * signature that proves the message comes from this shop (RFC 8292). Browsers' push services
 * (Google, Mozilla, Apple, Microsoft) deliver the encrypted message to the phone; only that phone can read it.
 */
public final class WebPush {
    private static final SecureRandom RANDOM = new SecureRandom();
    static final ECParameterSpec P256 = p256();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private WebPush() {
    }

    /** The shop's VAPID key pair: public as the 65-byte point, private as the 32-byte number, both base64url. */
    public record Vapid(String publicKey, String privateKey) {
        public static Vapid generate() {
            KeyPair kp = newKeyPair();
            return new Vapid(b64(point((ECPublicKey) kp.getPublic())), b64(fixed(((ECPrivateKey) kp.getPrivate()).getS().toByteArray(), 32)));
        }
    }

    /** Encrypts the payload for one browser subscription; returns the request body to POST to its endpoint. */
    public static byte[] encrypt(byte[] payload, String p256dh, String auth) {
        try {
            byte[] uaPublic = b64d(p256dh);
            byte[] authSecret = b64d(auth);
            if (uaPublic.length != 65 || uaPublic[0] != 4 || authSecret.length != 16) {
                throw new IllegalArgumentException("bad subscription keys");
            }
            KeyPair as = newKeyPair();
            byte[] asPublic = point((ECPublicKey) as.getPublic());
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(as.getPrivate());
            ka.doPhase(publicKey(uaPublic), true);
            byte[] ecdh = ka.generateSecret();

            byte[] prkKey = hmac(authSecret, ecdh);
            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic, new byte[]{1});
            byte[] ikm = hmac(prkKey, keyInfo);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 12);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cipher = c.doFinal(concat(payload, new byte[]{2})); // 0x02 = last (only) record, no padding

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + 65);
            header.put(salt).putInt(4096).put((byte) 65).put(asPublic);
            return concat(header.array(), cipher);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Web push encryption failed", e);
        }
    }

    /** The Authorization header value for an endpoint: "vapid t=<signed token>, k=<public key>". */
    public static String authorization(String endpoint, Vapid vapid, String subject, long nowSeconds) {
        try {
            URI u = URI.create(endpoint);
            String aud = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
            String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = b64(("{\"aud\":\"" + aud + "\",\"exp\":" + (nowSeconds + 12 * 3600) + ",\"sub\":\"" + subject + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, b64d(vapid.privateKey())), P256)));
            s.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
            String jwt = header + "." + claims + "." + b64(derToRaw(s.sign()));
            return "vapid t=" + jwt + ", k=" + vapid.publicKey();
        } catch (Exception e) {
            throw new IllegalStateException("VAPID signing failed", e);
        }
    }

    // ---------------------------------------------------------------- helpers

    static KeyPair newKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static ECPublicKey publicKey(byte[] point) throws Exception {
        byte[] x = Arrays.copyOfRange(point, 1, 33);
        byte[] y = Arrays.copyOfRange(point, 33, 65);
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), P256));
    }

    static byte[] point(ECPublicKey k) {
        return concat(new byte[]{4}, fixed(k.getW().getAffineX().toByteArray(), 32), fixed(k.getW().getAffineY().toByteArray(), 32));
    }

    /** Big-endian number to exactly n bytes (drops a sign byte, left-pads short values). */
    static byte[] fixed(byte[] v, int n) {
        if (v.length == n) return v;
        byte[] out = new byte[n];
        if (v.length > n) System.arraycopy(v, v.length - n, out, 0, n);
        else System.arraycopy(v, 0, out, n - v.length, v.length);
        return out;
    }

    /** Java signs ECDSA in DER; JWT wants the two 32-byte numbers side by side. */
    static byte[] derToRaw(byte[] der) {
        int offset = 2;
        if ((der[1] & 0xff) == 0x81) offset = 3;
        int rLen = der[offset + 1];
        byte[] r = Arrays.copyOfRange(der, offset + 2, offset + 2 + rLen);
        int sStart = offset + 2 + rLen;
        int sLen = der[sStart + 1];
        byte[] s = Arrays.copyOfRange(der, sStart + 2, sStart + 2 + sLen);
        return concat(fixed(r, 32), fixed(s, 32));
    }

    static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    static byte[] b64d(String s) {
        return B64D.decode(s.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
