package hackathon.app.conversation.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations", uniqueConstraints =
        @UniqueConstraint(name = "uk_conversations_schedule", columnNames = "schedule_id"))
public class Conversation {
    @Id @Column(length = 36, nullable = false)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long ownerUserId;
    @Column(name = "schedule_id")
    private Long scheduleId;
    @Column(length = 200)
    private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ConversationStatus status;
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Conversation() {}

    public static Conversation create(Long ownerUserId, String title, LocalDateTime now) {
        Conversation value = new Conversation();
        value.id = UUID.randomUUID().toString();
        value.ownerUserId = ownerUserId;
        value.title = title;
        value.status = ConversationStatus.ACTIVE;
        value.createdAt = now;
        value.updatedAt = now;
        return value;
    }

    public void archive(LocalDateTime now) { status = ConversationStatus.ARCHIVED; updatedAt = now; }
    public void messageAdded(LocalDateTime now) { lastMessageAt = now; updatedAt = now; }
    public void linkSchedule(Long scheduleId, LocalDateTime now) {
        this.scheduleId = scheduleId;
        this.updatedAt = now;
    }
    public void rename(String value, LocalDateTime now) { title = value; updatedAt = now; }
    public void attachSchedule(Long value, LocalDateTime now) { scheduleId = value; updatedAt = now; }
    public String getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Long getScheduleId() { return scheduleId; }
    public String getTitle() { return title; }
    public ConversationStatus getStatus() { return status; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
