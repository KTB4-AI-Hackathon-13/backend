package hackathon.app.conversation.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_messages", uniqueConstraints =
        @UniqueConstraint(name = "uk_messages_sequence", columnNames = {"conversation_id", "sequence_no"}))
public class ConversationMessage {
    @Id @Column(length = 36, nullable = false)
    private String id;
    @Column(name = "conversation_id", length = 36, nullable = false)
    private String conversationId;
    @Column(name = "parent_message_id", length = 36)
    private String parentMessageId;
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private MessageRole role;
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;
    @Lob
    @Column(name = "payload_json", columnDefinition = "JSON")
    private String payloadJson;
    @Column(name = "action", length = 30)
    private String action;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String content;
    @Column(name = "replaces_message_id", length = 36)
    private String replacesMessageId;
    @Column(name = "model_name", length = 100)
    private String modelName;
    @Column(name = "prompt_tokens")
    private Integer promptTokens;
    @Column(name = "completion_tokens")
    private Integer completionTokens;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected ConversationMessage() {}

    public static ConversationMessage create(String conversationId, String parentMessageId, int sequenceNo,
            MessageRole role, String action, String content, String replacesMessageId, String modelName,
            Integer promptTokens, Integer completionTokens, LocalDateTime now) {
        return create(conversationId, parentMessageId, sequenceNo, role, MessageType.TEXT, null,
                action, content, replacesMessageId, modelName, promptTokens, completionTokens, now);
    }

    public static ConversationMessage create(String conversationId, String parentMessageId, int sequenceNo,
            MessageRole role, MessageType messageType, String payloadJson,
            String action, String content, String replacesMessageId, String modelName,
            Integer promptTokens, Integer completionTokens, LocalDateTime now) {
        ConversationMessage value = new ConversationMessage();
        value.id = UUID.randomUUID().toString();
        value.conversationId = conversationId;
        value.parentMessageId = parentMessageId;
        value.sequenceNo = sequenceNo;
        value.role = role;
        value.messageType = messageType == null ? MessageType.TEXT : messageType;
        value.payloadJson = payloadJson;
        value.action = action;
        value.content = content;
        value.replacesMessageId = replacesMessageId;
        value.modelName = modelName;
        value.promptTokens = promptTokens;
        value.completionTokens = completionTokens;
        value.createdAt = now;
        return value;
    }

    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getParentMessageId() { return parentMessageId; }
    public int getSequenceNo() { return sequenceNo; }
    public MessageRole getRole() { return role; }
    public MessageType getMessageType() { return messageType; }
    public String getPayloadJson() { return payloadJson; }
    public String getAction() { return action; }
    public String getContent() { return content; }
    public String getReplacesMessageId() { return replacesMessageId; }
    public String getModelName() { return modelName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
