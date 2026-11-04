package com.example.ecommerce.service.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** The phone's side of RFC 8291 / 8292, to prove what the shop sends can be read and verified. */
class WebPushTest {

    @Test
    void aPhoneCanDecryptTheMessage() throws Exception {
        KeyPair phone = WebPush.newKeyPair();
        byte[] uaPublic = WebPush.point((ECPublicKey) phone.getPublic());
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String text = "{\"title\":\"New paid order ₹4,499\",\"body\":\"Priya · Kanchi silk\"}";

        byte[] body = WebPush.encrypt(text.getBytes(StandardCharsets.UTF_8), WebPush.b64(uaPublic), WebPush.b64(auth));

        // Receiver: parse the aes128gcm header, redo the key agreement with the phone's private key, decrypt.
        ByteBuffer h = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        h.get(salt);
        assertEquals(4096, h.getInt());
        assertEquals(65, h.get());
        byte[] asPublic = new byte[65];
        h.get(asPublic);
        byte[] cipher = Arrays.copyOfRange(body, 16 + 4 + 1 + 65, body.length);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(phone.getPrivate());
        ka.doPhase(WebPush.publicKey(asPublic), true);
        byte[] ecdh = ka.generateSecret();
        byte[] prkKey = WebPush.hmac(auth, ecdh);
        byte[] ikm = WebPush.hmac(prkKey, WebPush.concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic, new byte[]{1}));
        byte[] prk = WebPush.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPush.hmac(prk, WebPush.concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 16);
        byte[] nonce = Arrays.copyOf(WebPush.hmac(prk, WebPush.concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = c.doFinal(cipher);

        assertEquals(2, plain[plain.length - 1], "last-record delimiter");
        assertEquals(text, new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8));
    }

    @Test
    void theSignatureVerifiesWithTheShopsPublicKey() throws Exception {
        WebPush.Vapid v = WebPush.Vapid.generate();
        assertEquals(65, WebPush.b64d(v.publicKey()).length);
        assertEquals(32, WebPush.b64d(v.privateKey()).length);

        String header = WebPush.authorization("https://fcm.googleapis.com/fcm/send/abc123", v, "mailto:a@b.test", 1_800_000_000L);
        assertTrue(header.startsWith("vapid t="));
        assertTrue(header.endsWith(", k=" + v.publicKey()));
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(claims.contains("\"aud\":\"https://fcm.googleapis.com\""), claims);
        assertTrue(claims.contains("\"exp\":" + (1_800_000_000L + 12 * 3600)), claims);

        byte[] raw = Base64.getUrlDecoder().decode(parts[2]);
        assertEquals(64, raw.length);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(WebPush.publicKey(WebPush.b64d(v.publicKey())));
        s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(s.verify(rawToDer(raw)));
    }

    @Test
    void badSubscriptionKeysAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> WebPush.encrypt(new byte[]{1}, WebPush.b64(new byte[10]), WebPush.b64(new byte[16])));
    }

    private static byte[] rawToDer(byte[] raw) {
        byte[] r = new BigInteger(1, Arrays.copyOfRange(raw, 0, 32)).toByteArray();
        byte[] s = new BigInteger(1, Arrays.copyOfRange(raw, 32, 64)).toByteArray();
        byte[] out = new byte[6 + r.length + s.length];
        out[0] = 0x30;
        out[1] = (byte) (4 + r.length + s.length);
        out[2] = 0x02;
        out[3] = (byte) r.length;
        System.arraycopy(r, 0, out, 4, r.length);
        out[4 + r.length] = 0x02;
        out[5 + r.length] = (byte) s.length;
        System.arraycopy(s, 0, out, 6 + r.length, s.length);
        return out;
    }
}
