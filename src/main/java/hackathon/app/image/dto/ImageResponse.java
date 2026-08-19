package hackathon.app.image.dto;

import java.time.Instant;
import hackathon.app.image.entity.ImageOwnerType;
import hackathon.app.image.entity.StoredImage;
import hackathon.app.image.service.ImageService;

public record ImageResponse(Long id, String url, Instant urlExpiresAt, String contentType,
        long byteSize, Integer width, Integer height, String originalFilename,
        ImageOwnerType ownerType, String ownerId) {

    public static ImageResponse from(ImageService.ImageResult result) {
        StoredImage image = result.image();
        return new ImageResponse(image.getId(), result.url(), result.expiresAt(), image.getContentType(),
            image.getByteSize(), image.getWidth(), image.getHeight(), image.getOriginalFilename(),
            image.getOwnerType(), image.getOwnerId());
    }
}
