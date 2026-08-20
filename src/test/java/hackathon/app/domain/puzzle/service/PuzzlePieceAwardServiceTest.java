package hackathon.app.domain.puzzle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.repository.PuzzleCountProjection;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.image.service.ImageService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PuzzlePieceAwardServiceTest {

    private final PuzzleRepository puzzleRepository = mock(PuzzleRepository.class);
    private final PuzzlePieceRepository pieceRepository = mock(PuzzlePieceRepository.class);
    private final ScheduleItemRepository itemRepository = mock(ScheduleItemRepository.class);
    private final ImageService imageService = mock(ImageService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-20T21:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final PuzzlePieceAwardService service = new PuzzlePieceAwardService(
            puzzleRepository, pieceRepository, itemRepository, imageService, clock);

    @Test
    void firstCompletedItemCreatesPuzzleWithScheduleCategoryImage() {
        Schedule schedule = Schedule.builder()
                .userId(5L)
                .categoryId(7L)
                .title("노래 실력 향상")
                .status(ScheduleStatus.ACTIVE)
                .source(ChangeSource.AI)
                .startDate(LocalDate.of(2026, 8, 21))
                .endDate(LocalDate.of(2026, 9, 4))
                .build();
        ReflectionTestUtils.setField(schedule, "id", 12L);
        ScheduleItem item = ScheduleItem.builder()
                .schedule(schedule)
                .title("복식 호흡 익히기")
                .scheduledDate(LocalDate.of(2026, 8, 21))
                .estimatedMinutes(30)
                .build();
        ReflectionTestUtils.setField(item, "id", 136L);
        PuzzleCountProjection counts = mock(PuzzleCountProjection.class);
        when(counts.getPuzzleCount()).thenReturn(15L);
        when(puzzleRepository.findByScheduleId(12L)).thenReturn(Optional.empty());
        when(imageService.findRandomImageIdByCategoryId(7L)).thenReturn(Optional.of(77L));
        when(puzzleRepository.save(any(Puzzle.class))).thenAnswer(invocation -> {
            Puzzle puzzle = invocation.getArgument(0);
            ReflectionTestUtils.setField(puzzle, "id", 6L);
            return puzzle;
        });
        when(pieceRepository.findByScheduleItemId(136L)).thenReturn(Optional.empty());
        when(pieceRepository.nextPosition(6L)).thenReturn(0);
        when(pieceRepository.save(any(PuzzlePiece.class))).thenAnswer(invocation -> {
            PuzzlePiece piece = invocation.getArgument(0);
            ReflectionTestUtils.setField(piece, "id", 33L);
            return piece;
        });
        when(pieceRepository.countValidByPuzzleId(6L, hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus.CANCELLED))
                .thenReturn(1L);
        when(itemRepository.countPuzzlesByScheduleIds(
                List.of(12L),
                hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus.COMPLETED,
                hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus.CANCELLED))
                .thenReturn(List.of(counts));

        service.awardOnComplete(item);

        ArgumentCaptor<Puzzle> puzzleCaptor = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(puzzleCaptor.capture());
        assertThat(puzzleCaptor.getValue().getImageId()).isEqualTo(77L);
    }
}
