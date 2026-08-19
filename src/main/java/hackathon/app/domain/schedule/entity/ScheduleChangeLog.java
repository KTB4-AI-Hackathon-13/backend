package hackathon.app.domain.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * schedule_change_logs 테이블. 스케줄/작업 변경 전·후 스냅샷을 보존한다 (삭제하지 않음).
 * before_data / after_data 는 JSON 문자열로 저장한다.
 */
@Entity
@Table(name = "schedule_change_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "schedule_item_id")
    private Long scheduleItemId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ChangeAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ChangeSource source;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "before_data", columnDefinition = "json")
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "json")
    private String afterData;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "batch_job_id")
    private Long batchJobId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ScheduleChangeLog(Long scheduleId, Long scheduleItemId, Long actorUserId, ChangeAction action,
                              ChangeSource source, int version, String beforeData, String afterData,
                              String reason, Long batchJobId) {
        this.scheduleId = scheduleId;
        this.scheduleItemId = scheduleItemId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.source = source;
        this.version = version;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.reason = reason;
        this.batchJobId = batchJobId;
    }
}
