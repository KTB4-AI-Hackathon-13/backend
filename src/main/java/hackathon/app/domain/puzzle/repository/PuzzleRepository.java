package hackathon.app.domain.puzzle.repository;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long>, JpaSpecificationExecutor<Puzzle> {

    /** 스케줄당 퍼즐 1개 (uk_puzzle_schedule) */
    Optional<Puzzle> findByScheduleId(Long scheduleId);

    /** 사용자별 현재 완성 상태인 작품 수. */
    @Query("""
            SELECT p.userId AS userId, COUNT(p) AS puzzleCount
            FROM Puzzle p
            WHERE p.userId IN :userIds
              AND p.status = :status
            GROUP BY p.userId
            """)
    List<CompletedPuzzleCountProjection> countCompletedByUserIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("status") PuzzleStatus status);

    /** 기간·카테고리 랭킹을 위한 완성 퍼즐 원본. */
    @Query("""
            SELECT p.id AS puzzleId,
                   p.scheduleId AS scheduleId,
                   p.userId AS userId,
                   p.completedAt AS completedAt
            FROM Puzzle p
            WHERE p.userId IN :userIds
              AND p.status = :status
            """)
    List<CompletedPuzzleRankingProjection> findCompletedRankingFacts(
            @Param("userIds") Collection<Long> userIds,
            @Param("status") PuzzleStatus status);

    interface CompletedPuzzleCountProjection {
        Long getUserId();

        long getPuzzleCount();
    }

    interface CompletedPuzzleRankingProjection {
        Long getPuzzleId();

        Long getScheduleId();

        Long getUserId();

        java.time.LocalDateTime getCompletedAt();
    }
}
