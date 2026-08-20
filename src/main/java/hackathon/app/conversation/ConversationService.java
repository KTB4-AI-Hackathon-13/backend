package hackathon.app.conversation;

import hackathon.app.conversation.domain.*;
import hackathon.app.conversation.dto.response.*;
import hackathon.app.domain.schedule.repository.ScheduleRepository;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class ConversationService {
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final Clock clock;
    private final ScheduleRepository schedules;
    private final ObjectMapper objectMapper;
    private final ScheduleItemRepository scheduleItems;

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages,
            Clock clock, ScheduleRepository schedules, ObjectMapper objectMapper,
            ScheduleItemRepository scheduleItems) {
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
        this.schedules = schedules;
        this.objectMapper = objectMapper;
        this.scheduleItems = scheduleItems;
    }

    @Transactional
    public ConversationResponse create(Long userId, String title) {
        return ConversationResponse.from(conversations.save(Conversation.create(userId, title, now())));
    }

    @Transactional
    public CursorPageResponse<ConversationResponse> list(Long userId, int size, String cursor) {
        List<Conversation> found = conversations.findPage(userId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = found.size() > size;
        List<Conversation> page = hasNext ? found.subList(0, size) : found;
        page.forEach(this::replaceLegacyTitle);
        String next = hasNext ? page.getLast().getId() : null;
        return new CursorPageResponse<>(page.stream().map(ConversationResponse::from).toList(), next, hasNext);
    }

    public ConversationResponse find(Long userId, String conversationId) {
        return ConversationResponse.from(owned(userId, conversationId));
    }

    private void replaceLegacyTitle(Conversation conversation) {
        if (!"나의 새로운 계획".equals(conversation.getTitle())) return;

        String replacement = conversation.getScheduleId() == null ? null
                : schedules.findById(conversation.getScheduleId()).map(value -> value.getTitle()).orElse(null);
        if (replacement == null || replacement.isBlank() || "나의 새로운 계획".equals(replacement)) {
            List<ConversationMessage> history = messages.findPage(
                    conversation.getId(), null, PageRequest.of(0, 1000));
            for (int index = history.size() - 1; index >= 0; index--) {
                ConversationMessage message = history.get(index);
                String content = message.getContent();
                if (message.getRole() == MessageRole.USER && content != null
                        && !content.isBlank() && !content.stripLeading().startsWith("{")) {
                    replacement = content;
                    break;
                }
            }
        }
        if (replacement != null && !replacement.isBlank()) {
            conversation.rename(replacement.substring(0, Math.min(replacement.length(), 200)), now());
        }
    }

    public ConversationResponse findBySchedule(Long userId, Long scheduleId) {
        Conversation conversation = conversations
                .findByScheduleIdAndOwnerUserIdAndDeletedAtIsNull(scheduleId, userId)
                .orElseThrow(this::notFound);
        return ConversationResponse.from(conversation);
    }

    public ScheduleConversationResponse findByScheduleWithResumeContext(Long userId, Long scheduleId) {
        Conversation conversation = conversations
                .findByScheduleIdAndOwnerUserIdAndDeletedAtIsNull(scheduleId, userId)
                .orElseThrow(this::notFound);
        return ScheduleConversationResponse.from(
                conversation, resumeContext(conversation.getId(), scheduleId));
    }

    @SuppressWarnings("unchecked")
    private ConversationResumeContext resumeContext(String conversationId, Long scheduleId) {
        List<ConversationMessage> found = messages.findPage(
                conversationId, null, PageRequest.of(0, 1000));
        JsonNode requestNode = null;
        JsonNode resultNode = null;

        for (ConversationMessage message : found) {
            JsonNode node = parseJson(message.getPayloadJson());
            if (node == null) node = parseJson(message.getContent());
            if (node == null || !node.isObject()) continue;
            if (resultNode == null && node.get("plan") != null) resultNode = node;
            if (requestNode == null
                    && field(node, "goal_summary", "goalSummary") != null
                    && field(node, "template_answers", "templateAnswers") != null) {
                requestNode = node;
            }
            if (requestNode != null && resultNode != null) break;
        }
        if (requestNode == null || resultNode == null) return null;

        JsonNode plan = resultNode.get("plan");
        JsonNode answers = field(requestNode, "template_answers", "templateAnswers");
        if (plan == null || answers == null) return null;
        try {
            JsonNode feedback = field(resultNode, "feedback_history", "feedbackHistory");
            hackathon.app.ai.plan.dto.SchedulePlan savedPlan = objectMapper.treeToValue(
                    plan, hackathon.app.ai.plan.dto.SchedulePlan.class);
            var storedItems = scheduleItems
                    .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(scheduleId);
            List<hackathon.app.ai.plan.dto.DailyTask> allTasks = storedItems
                    .stream()
                    .map(item -> new hackathon.app.ai.plan.dto.DailyTask(
                            String.valueOf(item.getId()), item.getScheduledDate(), item.getTitle(),
                            item.getDescription(), item.getEstimatedMinutes()))
                    .toList();
            List<String> completedTaskIds = storedItems.stream()
                    .filter(item -> item.getStatus() == hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus.COMPLETED)
                    .map(item -> String.valueOf(item.getId()))
                    .toList();
            return new ConversationResumeContext(
                    field(requestNode, "goal_summary", "goalSummary").asText(),
                    requestNode.get("category").asText(),
                    objectMapper.convertValue(answers, Map.class),
                    new hackathon.app.ai.plan.dto.SchedulePlan(savedPlan.summary(), allTasks),
                    feedback == null ? List.of() : objectMapper.convertValue(feedback, List.class),
                    completedTaskIds);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank() || !value.stripLeading().startsWith("{")) return null;
        try {
            return objectMapper.readTree(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private JsonNode field(JsonNode node, String snakeCase, String camelCase) {
        JsonNode value = node.get(snakeCase);
        return value == null ? node.get(camelCase) : value;
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
        return send(userId, conversationId, content, null);
    }

    @Transactional
    public MessageResponse send(Long userId, String conversationId, String content, String payloadJson) {
        Conversation conversation = lockedActive(userId, conversationId);
        return appendUser(conversation, content, payloadJson, null, latestMessageId(conversationId));
    }

    @Transactional
    public MessageResponse appendAssistant(Long userId, String conversationId, String content) {
        return appendAssistant(userId, conversationId, content, null, null);
    }

    @Transactional
    public MessageResponse appendAssistant(Long userId, String conversationId, String content,
            String payloadJson, String action) {
        Conversation conversation = lockedActive(userId, conversationId);
        int sequence = messages.findMaxSequenceNo(conversationId) + 1;
        LocalDateTime createdAt = now();
        ConversationMessage assistant = messages.save(ConversationMessage.create(
                conversationId, latestMessageId(conversationId), sequence, MessageRole.ASSISTANT,
                MessageType.TEXT, payloadJson, action, content, null, null, null, null, createdAt));
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
        return appendUser(conversation, content, null, original.getId(), original.getParentMessageId());
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

    private MessageResponse appendUser(Conversation conversation, String content, String payloadJson,
            String replacesId, String parentId) {
        int sequence = messages.findMaxSequenceNo(conversation.getId()) + 1;
        LocalDateTime userTime = now();
        ConversationMessage userMessage = messages.save(ConversationMessage.create(conversation.getId(), parentId,
                sequence, MessageRole.USER, MessageType.TEXT, payloadJson, null, content,
                replacesId, null, null, null, userTime));
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
