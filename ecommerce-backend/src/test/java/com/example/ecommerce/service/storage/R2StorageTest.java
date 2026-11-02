package com.example.ecommerce.service.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class R2StorageTest {

    /** AWS's published Signature V4 example for S3 ("GET Object" with a Range header). */
    @Test
    void signatureMatchesTheAwsReferenceExample() {
        String auth = R2Storage.sign("GET", "examplebucket.s3.amazonaws.com", "/test.txt", Map.of("range", "bytes=0-9"),
                new byte[0], "20130524T000000Z", "us-east-1",
                "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, "
                + "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, "
                + "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", auth);
    }

    @Test
    void pathSegmentsAreEncodedButSlashesKept() {
        assertEquals("/bucket/media/a%20b%2B%C3%A9.jpg", R2Storage.encodePath("/bucket/media/a b+é.jpg"));
        assertEquals("/bucket/media/2026/x-y_z.~1.mp4", R2Storage.encodePath("/bucket/media/2026/x-y_z.~1.mp4"));
    }

    @Test
    void offUntilEverySettingIsPresent() {
        assertFalse(new R2Storage("", "", "", "", "").enabled());
        assertFalse(new R2Storage("acc", "key", "secret", "bucket", "http://insecure").enabled());
        R2Storage on = new R2Storage("acc", "key", "secret", "bucket", "https://pub-x.r2.dev/");
        assertTrue(on.enabled());
        assertEquals("https://pub-x.r2.dev/media/a.jpg", on.publicUrl("media/a.jpg"));
        assertNotNull("x".getBytes(StandardCharsets.UTF_8));
    }
}
