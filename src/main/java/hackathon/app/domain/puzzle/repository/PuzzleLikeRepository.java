package hackathon.app.domain.puzzle.repository;

import hackathon.app.domain.puzzle.entity.PuzzleLike;
import hackathon.app.domain.puzzle.entity.PuzzleLikeId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PuzzleLikeRepository extends JpaRepository<PuzzleLike, PuzzleLikeId> {
    long countByIdPuzzleId(Long puzzleId);
    boolean existsByIdPuzzleIdAndIdUserId(Long puzzleId, Long userId);
    long deleteByIdPuzzleIdAndIdUserId(Long puzzleId, Long userId);

    @Query("""
            SELECT l.id.puzzleId AS puzzleId, COUNT(l) AS likeCount
            FROM PuzzleLike l
            WHERE l.id.puzzleId IN :puzzleIds
            GROUP BY l.id.puzzleId
            """)
    List<LikeCountProjection> countByPuzzleIds(@Param("puzzleIds") Collection<Long> puzzleIds);

    interface LikeCountProjection {
        Long getPuzzleId();
        long getLikeCount();
    }
}
