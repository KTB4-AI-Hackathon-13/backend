package hackathon.app.conversation;

import hackathon.app.conversation.domain.ConversationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, String> {
    @Query("select coalesce(max(m.sequenceNo), 0) from ConversationMessage m where m.conversationId = :conversationId")
    int findMaxSequenceNo(@Param("conversationId") String conversationId);

    Optional<ConversationMessage> findByIdAndConversationIdAndDeletedAtIsNull(String id, String conversationId);

    @Query("select m from ConversationMessage m where m.conversationId = :conversationId and m.deletedAt is null " +
            "and (:before is null or m.sequenceNo < :before) order by m.sequenceNo desc")
    List<ConversationMessage> findPage(@Param("conversationId") String conversationId,
            @Param("before") Integer before, Pageable pageable);
}
