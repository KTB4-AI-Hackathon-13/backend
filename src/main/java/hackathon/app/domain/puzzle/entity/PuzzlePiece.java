package hackathon.app.domain.puzzle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * puzzle_pieces 테이블. 작업(schedule_item) 1건 = 조각 1개.
 * schedule_item_id 가 UNIQUE 라 같은 작업에서 조각이 두 번 지급되지 않는다 (핵심 정책 4).
 * position 은 획득한 순서(0부터).
 */
@Entity
@Table(name = "puzzle_pieces")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PuzzlePiece {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "puzzle_id", nullable = false)
    private Long puzzleId;

    @Column(name = "schedule_item_id", nullable = false)
    private Long scheduleItemId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PuzzlePiece(Long puzzleId, Long scheduleItemId, int position, LocalDateTime earnedAt) {
        this.puzzleId = puzzleId;
        this.scheduleItemId = scheduleItemId;
        this.position = position;
        this.earnedAt = earnedAt;
    }
}
