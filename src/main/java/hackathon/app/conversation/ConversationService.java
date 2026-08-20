package hackathon.app.conversation;

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
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages,
            Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
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

    public ConversationResponse findBySchedule(Long userId, Long scheduleId) {
        Conversation conversation = conversations
                .findByScheduleIdAndOwnerUserIdAndDeletedAtIsNull(scheduleId, userId)
                .orElseThrow(this::notFound);
        return ConversationResponse.from(conversation);
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

    /** 외부 AI를 호출하기 전에 대화 소유권과 활성 상태만 확인한다. 메시지는 아직 저장하지 않는다. */
    public Conversation requireActive(Long userId, String conversationId) {
        Conversation conversation = owned(userId, conversationId);
        if (conversation.getStatus() == ConversationStatus.ARCHIVED) {
            throw ConversationException.archived();
        }
        return conversation;
    }

    @Transactional
    public MessageResponse send(Long userId, String conversationId, String content) {
        Conversation conversation = lockedActive(userId, conversationId);
        return appendUser(conversation, content, null, latestMessageId(conversationId));
    }

    @Transactional
    public MessageResponse appendAssistant(Long userId, String conversationId, String content) {
        Conversation conversation = lockedActive(userId, conversationId);
        int sequence = messages.findMaxSequenceNo(conversationId) + 1;
        LocalDateTime createdAt = now();
        ConversationMessage assistant = messages.save(ConversationMessage.create(
                conversationId, latestMessageId(conversationId), sequence, MessageRole.ASSISTANT,
                null, content, null, null, null, null, createdAt));
        conversation.messageAdded(createdAt);
        return MessageResponse.from(assistant);
    }

    @Transactional
    public void rename(Long userId, String conversationId, String title) {
        if (title == null || title.isBlank()) return;
        Conversation conversation = lockedActive(userId, conversationId);
        conversation.rename(title.substring(0, Math.min(title.length(), 200)), now());
    }

    @Transactional
    public MessageResponse revise(Long userId, String conversationId, String messageId, String content) {
        Conversation conversation = lockedActive(userId, conversationId);
        ConversationMessage original = messages.findByIdAndConversationIdAndDeletedAtIsNull(messageId, conversationId)
                .orElseThrow(ConversationException::messageNotFound);
        if (original.getRole() != MessageRole.USER) {
            throw ConversationException.messageNotFound();
        }
        return appendUser(conversation, content, original.getId(), original.getParentMessageId());
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

    private MessageResponse appendUser(Conversation conversation, String content, String replacesId, String parentId) {
        int sequence = messages.findMaxSequenceNo(conversation.getId()) + 1;
        LocalDateTime userTime = now();
        ConversationMessage userMessage = messages.save(ConversationMessage.create(conversation.getId(), parentId,
                sequence, MessageRole.USER, null, content, replacesId, null, null, null, userTime));
        conversation.messageAdded(userTime);
        return MessageResponse.from(userMessage);
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
