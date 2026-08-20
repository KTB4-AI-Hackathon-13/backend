package hackathon.app.ai.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_generation_jobs")
public class AiGenerationJob {
    @Id @Column(length = 36)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;
    @Column(name = "schedule_id")
    private Long scheduleId;
    @Column(name = "requested_schedule_id")
    private Long requestedScheduleId;
    @Column(name = "category_id")
    private Long categoryId;
    @Column(name = "request_title", length = 200)
    private String requestTitle;
    @Column(name = "instruction", length = 5000)
    private String instruction;
    @Enumerated(EnumType.STRING) @Column(name = "generation_type", nullable = false, length = 20)
    private AiGenerationType generationType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AiGenerationStatus status;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected AiGenerationJob() {}

    public static AiGenerationJob create(Long userId, String conversationId, Long requestedScheduleId,
                                         Long categoryId, String requestTitle, String instruction,
                                         AiGenerationType type, LocalDateTime now) {
        AiGenerationJob job = new AiGenerationJob();
        job.id = UUID.randomUUID().toString();
        job.userId = userId;
        job.conversationId = conversationId;
        job.requestedScheduleId = requestedScheduleId;
        job.categoryId = categoryId;
        job.requestTitle = requestTitle;
        job.instruction = instruction;
        job.generationType = type;
        job.status = AiGenerationStatus.PENDING;
        job.createdAt = now;
        return job;
    }

    public void start() { status = AiGenerationStatus.RUNNING; }
    public void succeed(Long scheduleId, LocalDateTime now) {
        this.scheduleId = scheduleId; status = AiGenerationStatus.SUCCEEDED; finishedAt = now;
    }
    public void fail(String reason, LocalDateTime now) {
        failureReason = reason == null ? "AI_GENERATION_FAILED" : reason.substring(0, Math.min(reason.length(), 1000));
        status = AiGenerationStatus.FAILED; finishedAt = now;
    }
    public boolean isRunning() { return status == AiGenerationStatus.PENDING || status == AiGenerationStatus.RUNNING; }
    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public String getConversationId() { return conversationId; }
    public Long getScheduleId() { return scheduleId; }
    public Long getRequestedScheduleId() { return requestedScheduleId; }
    public Long getCategoryId() { return categoryId; }
    public String getRequestTitle() { return requestTitle; }
    public String getInstruction() { return instruction; }
    public AiGenerationType getGenerationType() { return generationType; }
    public AiGenerationStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}
