package hackathon.app.domain.puzzle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record PuzzleLikeId(
        @Column(name = "puzzle_id") Long puzzleId,
        @Column(name = "user_id") Long userId
) implements Serializable {
    protected PuzzleLikeId() { this(null, null); }
}
