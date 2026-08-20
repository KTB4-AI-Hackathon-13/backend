package hackathon.app.domain.scheduleitem.entity;

import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * schedule_items 테이블. 스케줄 안의 날짜별 최소 작업 단위 = 퍼즐 조각 1개.
 * 소프트 삭제된 행은 기본 조회에서 제외된다.
 */
@Entity
@Table(name = "schedule_items")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    /** categories.id (FK). 카테고리 엔티티와의 연관은 해당 도메인 확정 후 추가 */
    @Column(name = "category_id")
    private Long categoryId;

    /** 상위 작업 schedule_items.id */
    @Column(name = "parent_item_id")
    private Long parentItemId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "workload", nullable = false)
    private int workload;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    /** 1 highest, 5 lowest */
    @Column(name = "priority", nullable = false, columnDefinition = "tinyint")
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ChangeSource source;

    /** 같은 날짜 안에서의 표시 순서 (0부터). 작을수록 먼저 표시 */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private ScheduleItem(Schedule schedule, Long categoryId, Long parentItemId, String title, String description,
                         LocalDate scheduledDate, Integer workload, Integer priority, Integer position,
                         ChangeSource source) {
        this.schedule = schedule;
        this.categoryId = categoryId;
        this.parentItemId = parentItemId;
        this.title = title;
        this.description = description;
        this.scheduledDate = scheduledDate;
        this.workload = workload != null ? workload : 1;
        this.priority = priority != null ? priority : 3;
        this.position = position != null ? position : 0;
        this.status = ScheduleItemStatus.TODO;
        this.source = source != null ? source : ChangeSource.USER;
    }

    public boolean isCompleted() {
        return this.status == ScheduleItemStatus.COMPLETED;
    }

    /**
     * 퍼즐 조각으로 세는 "유효한 작업"인지 (ERD 4.3 정의).
     * 소프트 삭제된 행은 조회 자체가 안 되므로 여기서는 CANCELLED 여부만 본다.
     */
    public boolean countsAsPuzzlePiece() {
        return this.status != ScheduleItemStatus.CANCELLED;
    }

    /** 작업 내용 수정. null 인 값은 변경하지 않는다. (상태는 changeStatus 로만 변경) */
    public void update(String title, String description, LocalDate scheduledDate, Long categoryId,
                       Integer workload, Integer priority, Integer position) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (scheduledDate != null) {
            this.scheduledDate = scheduledDate;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (workload != null) {
            this.workload = workload;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (position != null) {
            this.position = position;
        }
    }

    /** AI 계획 확정 결과를 적용한다. null 값은 기존 값을 유지한다. */
    public void applyAiPlan(String title, String description, LocalDate scheduledDate, Integer estimatedMinutes) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (scheduledDate != null) this.scheduledDate = scheduledDate;
        if (estimatedMinutes != null) this.estimatedMinutes = estimatedMinutes;
        this.source = ChangeSource.AI;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    /**
     * 상태 변경. COMPLETED 로 바뀌면 completed_at 을 찍고, COMPLETED 가 아니게 되면 지운다.
     * (퍼즐 조각은 한 번 지급되면 회수하지 않는다 — 핵심 정책 4)
     */
    public void changeStatus(ScheduleItemStatus newStatus, LocalDateTime now) {
        this.status = newStatus;
        if (newStatus == ScheduleItemStatus.COMPLETED) {
            if (this.completedAt == null) {
                this.completedAt = now;
            }
        } else {
            this.completedAt = null;
        }
    }

    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
