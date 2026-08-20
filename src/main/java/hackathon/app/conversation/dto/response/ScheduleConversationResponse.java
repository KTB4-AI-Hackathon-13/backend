package hackathon.app.conversation.dto.response;

import hackathon.app.conversation.domain.Conversation;
import hackathon.app.conversation.domain.ConversationStatus;
import java.time.OffsetDateTime;

public record ScheduleConversationResponse(
        String conversationId,
        String title,
        ConversationStatus status,
        Long scheduleId,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ConversationResumeContext resumeContext
) {
    public static ScheduleConversationResponse from(
            Conversation conversation, ConversationResumeContext resumeContext) {
        ConversationResponse value = ConversationResponse.from(conversation);
        return new ScheduleConversationResponse(
                value.conversationId(), value.title(), value.status(), value.scheduleId(),
                value.lastMessageAt(), value.createdAt(), value.updatedAt(), resumeContext);
    }
}
