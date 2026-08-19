package hackathon.app.image.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration urlDuration;

    public S3ObjectStorage(S3Client client, S3Presigner presigner,
            @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.presigned-url-minutes:10}") long minutes) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.urlDuration = Duration.ofMinutes(minutes);
    }

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType(contentType).contentLength((long) bytes.length).build(),
                RequestBody.fromBytes(bytes));
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.IMAGE_STORAGE_FAILED);
        }
    }

    @Override
    public SignedUrl signedGetUrl(String key) {
        try {
            var request = GetObjectPresignRequest.builder().signatureDuration(urlDuration)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()).build();
            var result = presigner.presignGetObject(request);
            return new SignedUrl(result.url().toString(), result.expiration());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.IMAGE_STORAGE_FAILED);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.IMAGE_STORAGE_FAILED);
        }
    }
}
