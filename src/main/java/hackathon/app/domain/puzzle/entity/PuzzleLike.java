package hackathon.app.domain.puzzle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "puzzle_likes")
public class PuzzleLike {
    @EmbeddedId
    private PuzzleLikeId id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PuzzleLike() {}

    public static PuzzleLike create(Long puzzleId, Long userId, LocalDateTime now) {
        PuzzleLike like = new PuzzleLike();
        like.id = new PuzzleLikeId(puzzleId, userId);
        like.createdAt = now;
        return like;
    }
}
