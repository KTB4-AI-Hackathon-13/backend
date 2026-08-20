package hackathon.app.image.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
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

        // 카테고리 기본 이미지 한 장을 여러 퍼즐이 공유하므로 images.owner_id가 아니라
        // 실제 FK인 puzzles.image_id를 기준으로 현재 사용자가 볼 수 있는 퍼즐이 있는지 확인한다.
        for (Puzzle puzzle : puzzles.findAllByImageId(image.getId())) {
            if (puzzle.isOwnedBy(viewerUserId)
                    || (puzzle.isCompleted() && puzzle.getVisibility() == PuzzleVisibility.PUBLIC)) {
                return;
            }
        }
        throw accessDenied();
    }

    private ApiException accessDenied() {
        return new ApiException(ErrorCode.IMAGE_ACCESS_DENIED);
    }
}
