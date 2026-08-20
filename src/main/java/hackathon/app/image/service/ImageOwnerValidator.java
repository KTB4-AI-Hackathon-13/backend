package hackathon.app.image.service;

import org.springframework.stereotype.Component;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.image.entity.ImageOwnerType;
import jakarta.persistence.EntityManager;

@Component
public class ImageOwnerValidator {
    private final EntityManager entityManager;
    public ImageOwnerValidator(EntityManager entityManager) { this.entityManager = entityManager; }

    public void validate(ImageOwnerType type, String ownerId, Long userId) {
        boolean exists;
        try {
            exists = switch (type) {
                case USER -> ownerId.equals(userId.toString());
                case SCHEDULE -> count("SELECT COUNT(*) FROM schedules WHERE id = ?1 AND user_id = ?2 AND deleted_at IS NULL", ownerId, userId) > 0;
                case SCHEDULE_ITEM -> count("SELECT COUNT(*) FROM schedule_items i WHERE i.id = ?1 AND i.user_id = ?2 AND i.deleted_at IS NULL", ownerId, userId) > 0;
                case MESSAGE -> count("SELECT COUNT(*) FROM conversation_messages m JOIN conversations c ON c.id = m.conversation_id WHERE m.id = ?1 AND c.user_id = ?2 AND m.deleted_at IS NULL", ownerId, userId) > 0;
                case PUZZLE -> count("SELECT COUNT(*) FROM puzzles WHERE id = ?1 AND user_id = ?2 AND deleted_at IS NULL", ownerId, userId) > 0;
            };
        } catch (RuntimeException exception) {
            exists = false;
        }
        if (!exists) throw new ApiException(ErrorCode.IMAGE_ACCESS_DENIED);
    }

    private long count(String sql, String ownerId, Long userId) {
        Object value = entityManager.createNativeQuery(sql).setParameter(1, ownerId)
            .setParameter(2, userId).getSingleResult();
        return ((Number) value).longValue();
    }
}
