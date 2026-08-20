package hackathon.app.image.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.image.entity.ImageOwnerType;
import hackathon.app.image.entity.StoredImage;

@Component
@Transactional(readOnly = true)
public class ImageReadAccessPolicy {
    private final PuzzleRepository puzzles;

    public ImageReadAccessPolicy(PuzzleRepository puzzles) {
        this.puzzles = puzzles;
    }

    public void check(StoredImage image, Long viewerUserId) {
        if (viewerUserId != null && viewerUserId.equals(image.getUploaderUserId())) return;
        if (image.getOwnerType() != ImageOwnerType.PUZZLE) throw accessDenied();

        Long puzzleId;
        try {
            puzzleId = Long.valueOf(image.getOwnerId());
        } catch (NumberFormatException exception) {
            throw accessDenied();
        }

        Puzzle puzzle = puzzles.findById(puzzleId).orElseThrow(this::accessDenied);
        if (puzzle.getDeletedAt() != null) {
            throw accessDenied();
        }
        if (puzzle.isOwnedBy(viewerUserId)
                || (puzzle.isCompleted() && puzzle.getVisibility() == PuzzleVisibility.PUBLIC)) {
            return;
        }
        throw accessDenied();
    }

    private ApiException accessDenied() {
        return new ApiException(ErrorCode.IMAGE_ACCESS_DENIED);
    }
}
