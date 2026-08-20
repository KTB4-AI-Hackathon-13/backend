package hackathon.app.image.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "images")
public class StoredImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "uploader_user_id", nullable = false)
    private Long uploaderUserId;
    @Column(name = "category_id")
    private Long categoryId;
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private ImageOwnerType ownerType;
    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;
    @Column(name = "storage_key", nullable = false, unique = true, length = 700)
    private String storageKey;
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "byte_size", nullable = false)
    private long byteSize;
    private Integer width;
    private Integer height;
    @Column(length = 128)
    private String checksum;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected StoredImage() {}

    public static StoredImage create(Long uploaderUserId, Long categoryId, ImageOwnerType ownerType, String ownerId,
            String storageKey, String originalFilename, String contentType, long byteSize,
            int width, int height, String checksum) {
        StoredImage image = new StoredImage();
        image.uploaderUserId = uploaderUserId;
        image.categoryId = categoryId;
        image.ownerType = ownerType;
        image.ownerId = ownerId;
        image.storageKey = storageKey;
        image.originalFilename = originalFilename;
        image.contentType = contentType;
        image.byteSize = byteSize;
        image.width = width;
        image.height = height;
        image.checksum = checksum;
        image.createdAt = LocalDateTime.now();
        return image;
    }

    public void delete() { if (deletedAt == null) deletedAt = LocalDateTime.now(); }
    public boolean isDeleted() { return deletedAt != null; }
    public Long getId() { return id; }
    public Long getUploaderUserId() { return uploaderUserId; }
    public Long getCategoryId() { return categoryId; }
    public ImageOwnerType getOwnerType() { return ownerType; }
    public String getOwnerId() { return ownerId; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getByteSize() { return byteSize; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getChecksum() { return checksum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
