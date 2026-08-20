package hackathon.app.image.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import hackathon.app.auth.service.AuthService;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.image.entity.ImageOwnerType;
import hackathon.app.image.entity.StoredImage;
import hackathon.app.image.repository.StoredImageRepository;
import hackathon.app.user.entity.User;

@Service
@Transactional
public class ImageService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    public record ImageResult(StoredImage image, String url, Instant expiresAt) {}
    private final StoredImageRepository images;
    private final ObjectStorage storage;
    private final ImageOwnerValidator ownerValidator;
    private final AuthService auth;

    public ImageService(StoredImageRepository images, ObjectStorage storage,
            ImageOwnerValidator ownerValidator, AuthService auth) {
        this.images = images; this.storage = storage; this.ownerValidator = ownerValidator; this.auth = auth;
    }

    public ImageResult upload(String sessionId, MultipartFile file, ImageOwnerType ownerType, String ownerId) {
        User user = auth.requireUser(sessionId);
        ownerValidator.validate(ownerType, ownerId, user.getId());
        ValidatedFile valid = validate(file);
        String key = "images/" + user.getId() + "/" + UUID.randomUUID() + "." + valid.extension();
        storage.upload(key, valid.bytes(), valid.contentType());
        try {
            StoredImage saved = images.save(StoredImage.create(user.getId(), ownerType, ownerId, key,
                safeFilename(file.getOriginalFilename()), valid.contentType(), valid.bytes().length,
                valid.width(), valid.height(), sha256(valid.bytes())));
            ObjectStorage.SignedUrl signed = storage.signedGetUrl(key);
            return new ImageResult(saved, signed.url(), signed.expiresAt());
        } catch (RuntimeException exception) {
            try { storage.delete(key); } catch (RuntimeException ignored) {}
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ImageResult get(String sessionId, Long imageId) {
        User user = auth.requireUser(sessionId);
        StoredImage image = active(imageId);
        if (!image.getUploaderUserId().equals(user.getId())) throw new ApiException(ErrorCode.IMAGE_ACCESS_DENIED);
        ObjectStorage.SignedUrl signed = storage.signedGetUrl(image.getStorageKey());
        return new ImageResult(image, signed.url(), signed.expiresAt());
    }

    public void delete(String sessionId, Long imageId) {
        User user = auth.requireUser(sessionId);
        StoredImage image = active(imageId);
        if (!image.getUploaderUserId().equals(user.getId())) throw new ApiException(ErrorCode.IMAGE_ACCESS_DENIED);
        image.delete();
        images.save(image);
        storage.delete(image.getStorageKey());
    }

    private StoredImage active(Long id) {
        return images.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ApiException(ErrorCode.IMAGE_NOT_FOUND));
    }

    private ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(ErrorCode.IMAGE_FILE_REQUIRED);
        if (file.getSize() > MAX_SIZE) throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = extension(file.getOriginalFilename());
        if (!CONTENT_TYPES.contains(contentType) || !EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        try {
            byte[] bytes = file.getBytes();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
                throw new ApiException(ErrorCode.INVALID_IMAGE_FILE);
            }
            return new ValidatedFile(bytes, contentType, extension, decoded.getWidth(), decoded.getHeight());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
    private String safeFilename(String filename) {
        if (filename == null) return null;
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private record ValidatedFile(byte[] bytes, String contentType, String extension, int width, int height) {}
}
