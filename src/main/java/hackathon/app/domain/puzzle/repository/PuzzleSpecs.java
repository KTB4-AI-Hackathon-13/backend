package hackathon.app.domain.puzzle.repository;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import org.springframework.data.jpa.domain.Specification;

/** 퍼즐 목록 조회용 동적 조건 */
public final class PuzzleSpecs {

    private PuzzleSpecs() {
    }

    public static Specification<Puzzle> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Puzzle> hasStatus(PuzzleStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Puzzle> hasVisibility(PuzzleVisibility visibility) {
        return (root, query, cb) -> cb.equal(root.get("visibility"), visibility);
    }

    /** id 내림차순 페이징에서 커서(마지막 id)보다 작은 행만 */
    public static Specification<Puzzle> idLessThan(Long cursorId) {
        return (root, query, cb) -> cursorId == null ? null : cb.lessThan(root.get("id"), cursorId);
    }
}
