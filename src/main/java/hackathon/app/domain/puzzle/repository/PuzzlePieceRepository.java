package hackathon.app.domain.puzzle.repository;

import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PuzzlePieceRepository extends JpaRepository<PuzzlePiece, Long> {

    /** 같은 작업으로 이미 지급된 조각이 있는지 (중복 지급 방지) */
    Optional<PuzzlePiece> findByScheduleItemId(Long scheduleItemId);

    List<PuzzlePiece> findByPuzzleIdOrderByPositionAsc(Long puzzleId);

    /**
     * 유효한 작업(삭제·CANCELLED 제외)에 대한 조각 수.
     * 작업이 지워지거나 취소되면 그 조각은 진행률에서 빠진다 (전체 칸 수도 같이 줄기 때문).
     */
    @Query("""
            SELECT COUNT(p) FROM PuzzlePiece p, ScheduleItem i
            WHERE p.scheduleItemId = i.id
              AND i.status <> :cancelled
              AND p.puzzleId = :puzzleId
            """)
    long countValidByPuzzleId(@Param("puzzleId") Long puzzleId,
                              @Param("cancelled") ScheduleItemStatus cancelled);

    /** 다음 position (= 현재 최대 + 1, 없으면 0) */
    @Query("SELECT COALESCE(MAX(p.position), -1) + 1 FROM PuzzlePiece p WHERE p.puzzleId = :puzzleId")
    int nextPosition(@Param("puzzleId") Long puzzleId);

    /** 목록 화면용: 퍼즐별 유효한 조각 수 한 번에 집계 */
    @Query("""
            SELECT p.puzzleId AS puzzleId, COUNT(p) AS pieceCount
            FROM PuzzlePiece p, ScheduleItem i
            WHERE p.scheduleItemId = i.id
              AND i.status <> :cancelled
              AND p.puzzleId IN :puzzleIds
            GROUP BY p.puzzleId
            """)
    List<PieceCountProjection> countValidByPuzzleIds(@Param("puzzleIds") Collection<Long> puzzleIds,
                                                     @Param("cancelled") ScheduleItemStatus cancelled);

    interface PieceCountProjection {
        Long getPuzzleId();

        long getPieceCount();
    }
}
