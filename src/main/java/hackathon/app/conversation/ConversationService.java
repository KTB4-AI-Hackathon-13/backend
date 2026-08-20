package hackathon.app.conversation;

import hackathon.app.conversation.ai.*;
import hackathon.app.conversation.domain.*;
import hackathon.app.conversation.dto.response.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ConversationService {
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final AiConversationClient ai;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages,
            AiConversationClient ai, Clock clock, tools.jackson.databind.ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.ai = ai;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConversationResponse create(Long userId, String title) {
        return ConversationResponse.from(conversations.save(Conversation.create(userId, title, now())));
    }

    public CursorPageResponse<ConversationResponse> list(Long userId, int size, String cursor) {
        List<Conversation> found = conversations.findPage(userId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = found.size() > size;
        List<Conversation> page = hasNext ? found.subList(0, size) : found;
        String next = hasNext ? page.getLast().getId() : null;
        return new CursorPageResponse<>(page.stream().map(ConversationResponse::from).toList(), next, hasNext);
    }

    public MessageScrollResponse messages(Long userId, String conversationId, int size, Integer before) {
        owned(userId, conversationId);
        List<ConversationMessage> found = messages.findPage(conversationId, before, PageRequest.of(0, size + 1));
        boolean hasNext = found.size() > size;
        List<ConversationMessage> page = hasNext ? found.subList(0, size) : found;
        List<MessageResponse> chronological = new ArrayList<>(page.stream().map(MessageResponse::from).toList());
        Collections.reverse(chronological);
        Integer next = hasNext ? page.getLast().getSequenceNo() : null;
        return new MessageScrollResponse(chronological, next, hasNext);
    }

    @Transactional
    public MessageExchangeResponse send(Long userId, String conversationId, String content) {
        Conversation conversation = lockedActive(userId, conversationId);
        return appendExchange(conversation, content, null, latestMessageId(conversationId));
    }

    @Transactional
    public MessageExchangeResponse revise(Long userId, String conversationId, String messageId, String content) {
        Conversation conversation = lockedActive(userId, conversationId);
        ConversationMessage original = messages.findByIdAndConversationIdAndDeletedAtIsNull(messageId, conversationId)
                .orElseThrow(ConversationException::messageNotFound);
        if (original.getRole() != MessageRole.USER) {
            throw ConversationException.messageNotFound();
        }
        return appendExchange(conversation, content, original.getId(), original.getParentMessageId());
    }

    @Transactional
    public ConversationResponse archive(Long userId, String conversationId, ConversationStatus status) {
        if (status != ConversationStatus.ARCHIVED) {
            throw ConversationException.invalidStatus();
        }
        Conversation conversation = conversations.findOwnedForUpdate(conversationId, userId)
                .orElseThrow(this::notFound);
        conversation.archive(now());
        return ConversationResponse.from(conversation);
    }

    private MessageExchangeResponse appendExchange(Conversation conversation, String content, String replacesId, String parentId) {
        int sequence = messages.findMaxSequenceNo(conversation.getId()) + 1;
        LocalDateTime userTime = now();
        ConversationMessage userMessage = messages.save(ConversationMessage.create(conversation.getId(), parentId,
                sequence, MessageRole.USER, null, content, replacesId, null, null, null, userTime));
        AiConversationResult result = ai.reply(content);
        LocalDateTime assistantTime = now();
        String payloadJson = result.planDraft() == null ? null : objectMapper.writeValueAsString(result.planDraft());
        MessageType messageType = payloadJson == null ? MessageType.TEXT : MessageType.PLAN_DRAFT;
        ConversationMessage assistant = messages.save(ConversationMessage.create(conversation.getId(), userMessage.getId(),
                sequence + 1, MessageRole.ASSISTANT, messageType, payloadJson,
                result.action().type().name(), result.content(), null, result.modelName(),
                result.tokenUsage().promptTokens(), result.tokenUsage().completionTokens(), assistantTime));
        conversation.messageAdded(assistantTime);
        return new MessageExchangeResponse(MessageResponse.from(userMessage), MessageResponse.from(assistant), readiness(content));
    }

    private PlanReadinessResponse readiness(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        boolean hasEndDate = normalized.matches(".*(\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}월|까지).*?");
        boolean hasDays = normalized.matches(".*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|평일|주말|매일).*?");
        if (!hasEndDate) missing.add("END_DATE");
        if (!hasDays) missing.add("AVAILABLE_DAYS");
        return new PlanReadinessResponse(missing.isEmpty(), List.copyOf(missing));
    }

    private String latestMessageId(String conversationId) {
        List<ConversationMessage> latest = messages.findPage(conversationId, null, PageRequest.of(0, 1));
        return latest.isEmpty() ? null : latest.getFirst().getId();
    }

    private Conversation lockedActive(Long userId, String id) {
        Conversation conversation = conversations.findOwnedForUpdate(id, userId)
                .orElseThrow(this::notFound);
        if (conversation.getStatus() == ConversationStatus.ARCHIVED) {
            throw ConversationException.archived();
        }
        return conversation;
    }

    private Conversation owned(Long userId, String id) {
        return conversations.findByIdAndOwnerUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(this::notFound);
    }

    private ConversationException notFound() {
        return ConversationException.notFound();
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
