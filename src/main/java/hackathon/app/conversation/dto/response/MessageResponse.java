package hackathon.app.conversation.dto.response;

import hackathon.app.conversation.domain.ConversationMessage;
import hackathon.app.conversation.domain.MessageRole;
import java.time.*;

public record MessageResponse(String id, String conversationId, String parentMessageId, int sequenceNo,
        MessageRole role, String action, String content, String replacesMessageId, String modelName,
        OffsetDateTime createdAt) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MessageResponse from(ConversationMessage value) {
        return new MessageResponse(value.getId(), value.getConversationId(), value.getParentMessageId(),
                value.getSequenceNo(), value.getRole(), value.getAction(), value.getContent(), value.getReplacesMessageId(),
                value.getModelName(), value.getCreatedAt().atZone(SEOUL).toOffsetDateTime());
    }
}
