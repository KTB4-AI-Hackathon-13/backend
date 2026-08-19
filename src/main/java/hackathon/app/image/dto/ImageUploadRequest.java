package hackathon.app.image.dto;

import org.springframework.web.multipart.MultipartFile;
import hackathon.app.image.entity.ImageOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImageUploadRequest(
        @NotNull MultipartFile file,
        @NotNull ImageOwnerType ownerType,
        @NotBlank String ownerId) {
}
