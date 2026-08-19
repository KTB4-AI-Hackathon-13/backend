package hackathon.app.image.service;

import java.time.Instant;

public interface ObjectStorage {
    record SignedUrl(String url, Instant expiresAt) {}
    void upload(String key, byte[] bytes, String contentType);
    SignedUrl signedGetUrl(String key);
    void delete(String key);
}
