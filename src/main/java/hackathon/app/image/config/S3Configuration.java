package hackathon.app.image.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Configuration {
    @Bean
    S3Client s3Client(@Value("${app.storage.region}") String region,
            @Value("${app.storage.endpoint:}") String endpoint,
            @Value("${app.storage.access-key:}") String accessKey,
            @Value("${app.storage.secret-key:}") String secretKey) {
        var builder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials(accessKey, secretKey));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(@Value("${app.storage.region}") String region,
            @Value("${app.storage.endpoint:}") String endpoint,
            @Value("${app.storage.access-key:}") String accessKey,
            @Value("${app.storage.secret-key:}") String secretKey) {
        var builder = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials(accessKey, secretKey));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    private AwsCredentialsProvider credentials(String accessKey, String secretKey) {
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
