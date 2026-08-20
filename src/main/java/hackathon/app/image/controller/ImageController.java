package hackathon.app.image.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.image.dto.ImageResponse;
import hackathon.app.image.dto.ImageUploadRequest;
import hackathon.app.image.service.ImageService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {
    private final ImageService service;
    public ImageController(ImageService service) { this.service = service; }

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<ApiResponse<ImageResponse>> upload(
            @CookieValue(name = "SESSION", required = false) String sessionId,
            @Valid @ModelAttribute ImageUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(ImageResponse.from(service.upload(sessionId, request.file(),
                request.ownerType(), request.ownerId(), request.categoryId()))));
    }

    @GetMapping("/{imageId}")
    ApiResponse<ImageResponse> get(@CookieValue(name = "SESSION", required = false) String sessionId,
            @PathVariable Long imageId) {
        return ApiResponse.of(ImageResponse.from(service.get(sessionId, imageId)));
    }

    @DeleteMapping("/{imageId}")
    ResponseEntity<Void> delete(@CookieValue(name = "SESSION", required = false) String sessionId,
            @PathVariable Long imageId) {
        service.delete(sessionId, imageId);
        return ResponseEntity.noContent().build();
    }
}
