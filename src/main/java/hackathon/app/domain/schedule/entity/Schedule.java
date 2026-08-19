package hackathon.app.domain.schedule.entity;

import hackathon.app.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * schedules 테이블. 사용자의 하나의 목표/계획 묶음.
 * 소프트 삭제된 행(deleted_at IS NOT NULL)은 기본 조회에서 제외된다.
 */
@Entity
@Table(name = "schedules")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** users.id (FK). User 엔티티와의 직접 연관은 회원 도메인 확정 후 필요 시 추가 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ChangeSource source;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Schedule(Long userId, String title, String description, ScheduleStatus status,
                     ChangeSource source, LocalDate startDate, LocalDate endDate) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.status = status != null ? status : ScheduleStatus.DRAFT;
        this.source = source != null ? source : ChangeSource.USER;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentVersion = 1;
    }

    // ===== 도메인 행위 =====

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean containsDate(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** 제목/설명/기간 수정. null 인 값은 변경하지 않는다. 버전을 1 올린다. */
    public void update(String title, String description, LocalDate startDate, LocalDate endDate) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
        increaseVersion();
    }

    /** 스케줄 또는 하위 작업에 변경 이력이 남을 때마다 버전을 1 올린다 (schedule_change_logs.version 과 대응) */
    public void increaseVersion() {
        this.currentVersion++;
    }

    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
