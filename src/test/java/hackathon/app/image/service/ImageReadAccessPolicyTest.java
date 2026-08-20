package hackathon.app.image.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.image.entity.StoredImage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageReadAccessPolicyTest {

    private final PuzzleRepository puzzleRepository = mock(PuzzleRepository.class);
    private final ImageReadAccessPolicy policy = new ImageReadAccessPolicy(puzzleRepository);

    @Test
    void allowsOwnerOfPuzzleThatReferencesSharedImage() {
        StoredImage image = image(77L, 999L);
        Puzzle puzzle = Puzzle.builder()
                .scheduleId(12L)
                .userId(5L)
                .imageId(77L)
                .title("노래 실력 향상")
                .build();
        when(puzzleRepository.findAllByImageId(77L)).thenReturn(List.of(puzzle));

        assertThatCode(() -> policy.check(image, 5L)).doesNotThrowAnyException();

        verify(puzzleRepository).findAllByImageId(77L);
    }

    @Test
    void allowsViewerWhenAnyReferencingPuzzleIsPublicAndCompleted() {
        StoredImage image = image(77L, 999L);
        Puzzle puzzle = Puzzle.builder()
                .scheduleId(12L)
                .userId(5L)
                .imageId(77L)
                .title("노래 실력 향상")
                .visibility(PuzzleVisibility.PUBLIC)
                .build();
        puzzle.refreshCompletion(1, 1, LocalDateTime.of(2026, 8, 21, 6, 0));
        when(puzzleRepository.findAllByImageId(77L)).thenReturn(List.of(puzzle));

        assertThatCode(() -> policy.check(image, 8L)).doesNotThrowAnyException();
    }

    @Test
    void deniesViewerWithoutAccessibleReferencingPuzzle() {
        StoredImage image = image(77L, 999L);
        when(puzzleRepository.findAllByImageId(77L)).thenReturn(List.of());

        assertThatThrownBy(() -> policy.check(image, 8L))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.IMAGE_ACCESS_DENIED);
    }

    private StoredImage image(Long id, Long uploaderUserId) {
        StoredImage image = mock(StoredImage.class);
        when(image.getId()).thenReturn(id);
        when(image.getUploaderUserId()).thenReturn(uploaderUserId);
        return image;
    }
}
