package hackathon.app.conversation.dto.response;

import hackathon.app.conversation.domain.ConversationMessage;
import hackathon.app.conversation.domain.MessageRole;
import hackathon.app.conversation.domain.MessageType;
import java.time.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record MessageResponse(String id, String conversationId, String parentMessageId, int sequenceNo,
        MessageRole role, String action, String content, String replacesMessageId, String modelName,
        MessageType messageType, JsonNode planDraft, OffsetDateTime createdAt) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MessageResponse from(ConversationMessage value) {
        return new MessageResponse(value.getId(), value.getConversationId(), value.getParentMessageId(),
                value.getSequenceNo(), value.getRole(), value.getAction(), value.getContent(), value.getReplacesMessageId(),
                value.getModelName(), value.getMessageType(), parse(value.getPayloadJson()),
                value.getCreatedAt().atZone(SEOUL).toOffsetDateTime());
    }

    private static JsonNode parse(String value) {
        return value == null ? null : JsonMapper.builder().build().readTree(value);
    }
}
