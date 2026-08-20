package hackathon.app.conversation.dto.response;

import hackathon.app.conversation.domain.Conversation;
import hackathon.app.conversation.domain.ConversationStatus;
import java.time.*;

public record ConversationResponse(String conversationId, String title, ConversationStatus status,
        Long scheduleId, OffsetDateTime lastMessageAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ConversationResponse from(Conversation value) {
        return new ConversationResponse(value.getId(), value.getTitle(), value.getStatus(), value.getScheduleId(),
                at(value.getLastMessageAt()), at(value.getCreatedAt()), at(value.getUpdatedAt()));
    }

    private static OffsetDateTime at(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
    }
}
