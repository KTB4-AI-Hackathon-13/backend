package hackathon.app.conversation;

import hackathon.app.conversation.domain.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Optional<Conversation> findByIdAndOwnerUserIdAndDeletedAtIsNull(String id, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.ownerUserId = :userId and c.deletedAt is null")
    Optional<Conversation> findOwnedForUpdate(@Param("id") String id, @Param("userId") Long userId);

    @Query("select c from Conversation c where c.ownerUserId = :userId and c.deletedAt is null " +
            "and (:cursor is null or c.id < :cursor) order by coalesce(c.lastMessageAt, c.createdAt) desc, c.id desc")
    List<Conversation> findPage(@Param("userId") Long userId, @Param("cursor") String cursor, Pageable pageable);
}
