package com.example.ecommerce.service.push;

import java.security.interfaces.ECPublicKey;

/** Lets tests outside this package make a browser-like subscription key. */
public final class WebPushAccess {
    private WebPushAccess() {
    }

    /** A fresh browser public key (p256dh), base64url. */
    public static String keyPair() {
        return WebPush.b64(WebPush.point((ECPublicKey) WebPush.newKeyPair().getPublic()));
    }
}
