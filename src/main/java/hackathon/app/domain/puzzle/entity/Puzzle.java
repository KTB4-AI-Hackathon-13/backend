package hackathon.app.domain.puzzle.entity;

import hackathon.app.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.SQLRestriction;

/**
 * puzzles 테이블. 스케줄 1개당 퍼즐 1개 (uk_puzzle_schedule).
 * 스케줄의 작업을 처음 완료할 때 자동 생성되고, 유효한 작업을 모두 완료하면 COMPLETED 가 된다.
 */
@Entity
@Table(name = "puzzles")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Puzzle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** images.id — 퍼즐 그림. 이미지 도메인 도입 전까지 null */
    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PuzzleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private PuzzleVisibility visibility;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Puzzle(Long scheduleId, Long userId, Long imageId, String title, PuzzleVisibility visibility) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.imageId = imageId;
        this.title = title;
        this.status = PuzzleStatus.IN_PROGRESS;
        this.visibility = visibility != null ? visibility : PuzzleVisibility.PUBLIC;
    }

    // ===== 도메인 행위 =====

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isCompleted() {
        return this.status == PuzzleStatus.COMPLETED;
    }

    public boolean isPublic() {
        return this.visibility == PuzzleVisibility.PUBLIC;
    }

    /**
     * 조각 수집 결과를 반영한다.
     * - 모든 조각을 모았으면 COMPLETED (completed_at 최초 1회만 기록)
     * - 완성 후 작업이 추가되면 다시 IN_PROGRESS 로 돌아간다
     */
    public void refreshCompletion(long earnedPieces, long totalPieces, LocalDateTime now) {
        boolean allCollected = totalPieces > 0 && earnedPieces >= totalPieces;
        if (allCollected) {
            this.status = PuzzleStatus.COMPLETED;
            if (this.completedAt == null) {
                this.completedAt = now;
            }
        } else {
            this.status = PuzzleStatus.IN_PROGRESS;
            this.completedAt = null;
        }
    }

    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
