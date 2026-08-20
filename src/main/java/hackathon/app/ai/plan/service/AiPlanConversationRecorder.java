package hackathon.app.ai.plan.service;

import hackathon.app.conversation.ConversationException;
import hackathon.app.conversation.ConversationMessageRepository;
import hackathon.app.conversation.ConversationRepository;
import hackathon.app.conversation.domain.Conversation;
import hackathon.app.conversation.domain.ConversationMessage;
import hackathon.app.conversation.domain.ConversationStatus;
import hackathon.app.conversation.domain.MessageRole;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** AI 계획 API 한 턴을 USER + ASSISTANT 메시지로 저장한다. */
@Service
@RequiredArgsConstructor
public class AiPlanConversationRecorder {
    public static final String TEMPLATE = "template";
    public static final String REJECT = "reject";
    public static final String PLAN_TURN = "plan_turn";
    public static final String PLAN_CONFIRMED = "plan_confirmed";

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public void append(Long userId, String conversationId, Object userContent,
                       Object assistantContent, String assistantAction) {
        Conversation conversation = conversationRepository.findOwnedForUpdate(conversationId, userId)
                .orElseThrow(ConversationException::notFound);
        if (conversation.getStatus() == ConversationStatus.ARCHIVED) {
            throw ConversationException.archived();
        }

        int sequence = messageRepository.findMaxSequenceNo(conversationId) + 1;
        String parentId = latestMessageId(conversationId);
        LocalDateTime now = LocalDateTime.now(clock);
        ConversationMessage userMessage = messageRepository.save(ConversationMessage.create(
                conversationId, parentId, sequence, MessageRole.USER, null, serialize(userContent),
                null, null, null, null, now));
        ConversationMessage assistantMessage = messageRepository.save(ConversationMessage.create(
                conversationId, userMessage.getId(), sequence + 1, MessageRole.ASSISTANT, assistantAction,
                serialize(assistantContent), null, null, null, null, now));
        conversation.messageAdded(now);
    }

    private String latestMessageId(String conversationId) {
        List<ConversationMessage> latest = messageRepository.findPage(
                conversationId, null, PageRequest.of(0, 1));
        return latest.isEmpty() ? null : latest.getFirst().getId();
    }

    private String serialize(Object value) {
        return value instanceof String text ? text : objectMapper.writeValueAsString(value);
    }
}
