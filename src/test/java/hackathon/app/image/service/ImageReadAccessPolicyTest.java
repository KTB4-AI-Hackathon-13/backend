package hackathon.app.image.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.image.entity.ImageOwnerType;
import hackathon.app.image.entity.StoredImage;

@ExtendWith(MockitoExtension.class)
class ImageReadAccessPolicyTest {
    private static final Long UPLOADER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PUZZLE_ID = 7L;

    @Mock PuzzleRepository puzzles;
    @InjectMocks ImageReadAccessPolicy policy;

    @Test
    void uploaderCanReadOwnImage() {
        StoredImage image = mock(StoredImage.class);
        when(image.getUploaderUserId()).thenReturn(UPLOADER_ID);

        assertThatCode(() -> policy.check(image, UPLOADER_ID)).doesNotThrowAnyException();
        verifyNoInteractions(puzzles);
    }

    @Test
    void loggedInOtherUserCanReadPublicCompletedPuzzleImage() {
        StoredImage image = puzzleImage(String.valueOf(PUZZLE_ID));
        Puzzle puzzle = puzzle(false, true);
        when(puzzles.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle));

        assertThatCode(() -> policy.check(image, OTHER_USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void anonymousUserCanReadPublicCompletedPuzzleImage() {
        StoredImage image = mock(StoredImage.class);
        when(image.getOwnerType()).thenReturn(ImageOwnerType.PUZZLE);
        when(image.getOwnerId()).thenReturn(String.valueOf(PUZZLE_ID));
        Puzzle puzzle = puzzle(false, true);
        when(puzzles.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle));

        assertThatCode(() -> policy.check(image, null)).doesNotThrowAnyException();
    }

    @Test
    void otherUserCanReadPrivateCompletedPuzzleImage() {
        StoredImage image = puzzleImage(String.valueOf(PUZZLE_ID));
        Puzzle puzzle = puzzle(false, true);
        when(puzzles.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle));

        assertThatCode(() -> policy.check(image, OTHER_USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void otherUserCannotReadInProgressPuzzleImage() {
        StoredImage image = puzzleImage(String.valueOf(PUZZLE_ID));
        Puzzle puzzle = puzzle(false, false);
        when(puzzles.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle));

        assertAccessDenied(() -> policy.check(image, OTHER_USER_ID));
    }

    @Test
    void otherUserCannotReadDeletedPuzzleImage() {
        StoredImage image = puzzleImage(String.valueOf(PUZZLE_ID));
        Puzzle puzzle = puzzle(true, false);
        when(puzzles.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle));

        assertAccessDenied(() -> policy.check(image, OTHER_USER_ID));
    }

    @Test
    void otherUserCannotReadNonPuzzleImage() {
        StoredImage image = mock(StoredImage.class);
        when(image.getUploaderUserId()).thenReturn(UPLOADER_ID);
        when(image.getOwnerType()).thenReturn(ImageOwnerType.USER);

        assertAccessDenied(() -> policy.check(image, OTHER_USER_ID));
        verifyNoInteractions(puzzles);
    }

    @Test
    void malformedPuzzleOwnerIdIsDenied() {
        StoredImage image = puzzleImage("not-a-number");

        assertAccessDenied(() -> policy.check(image, OTHER_USER_ID));
        verifyNoInteractions(puzzles);
    }

    @Test
    void missingPuzzleIsDenied() {
        StoredImage image = puzzleImage(String.valueOf(PUZZLE_ID));
        when(puzzles.findById(PUZZLE_ID)).thenReturn(Optional.empty());

        assertAccessDenied(() -> policy.check(image, OTHER_USER_ID));
    }

    private StoredImage puzzleImage(String ownerId) {
        StoredImage image = mock(StoredImage.class);
        when(image.getUploaderUserId()).thenReturn(UPLOADER_ID);
        when(image.getOwnerType()).thenReturn(ImageOwnerType.PUZZLE);
        when(image.getOwnerId()).thenReturn(ownerId);
        return image;
    }

    private Puzzle puzzle(boolean deleted, boolean completed) {
        Puzzle puzzle = mock(Puzzle.class);
        when(puzzle.getDeletedAt()).thenReturn(deleted ? LocalDateTime.now() : null);
        if (!deleted) when(puzzle.isCompleted()).thenReturn(completed);
        return puzzle;
    }

    private void assertAccessDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).errorCode())
            .isEqualTo(ErrorCode.IMAGE_ACCESS_DENIED);
    }
}
