package hackathon.app.domain.puzzle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.policy.PuzzlePieceAwarder;
import hackathon.app.domain.scheduleitem.repository.PuzzleCountProjection;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PuzzlePieceAwardServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SCHEDULE_ID = 104L;
    private static final Long ITEM_ID = 1302L;
    private static final Long PUZZLE_ID = 7L;

    @Mock PuzzleRepository puzzleRepository;
    @Mock PuzzlePieceRepository puzzlePieceRepository;
    @Mock ScheduleItemRepository scheduleItemRepository;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks PuzzlePieceAwardService service;

    private Schedule schedule;
    private ScheduleItem item;

    @BeforeEach
    void setUp() throws Exception {
        schedule = Schedule.builder().userId(USER_ID).title("운동 루틴").status(ScheduleStatus.ACTIVE)
                .source(ChangeSource.AI).startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 8, 31))
                .build();
        setId(schedule, SCHEDULE_ID);
        item = ScheduleItem.builder().schedule(schedule).title("상체 운동")
                .scheduledDate(LocalDate.of(2026, 8, 19)).build();
        setId(item, ITEM_ID);
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    private Puzzle puzzle() throws Exception {
        Puzzle p = Puzzle.builder().scheduleId(SCHEDULE_ID).userId(USER_ID).title("운동 루틴").build();
        setId(p, PUZZLE_ID);
        return p;
    }

    private void stubCounts(long earned, long total) {
        when(puzzlePieceRepository.countValidByPuzzleId(PUZZLE_ID, ScheduleItemStatus.CANCELLED)).thenReturn(earned);
        when(scheduleItemRepository.countPuzzlesByScheduleIds(List.of(SCHEDULE_ID), ScheduleItemStatus.COMPLETED,
                ScheduleItemStatus.CANCELLED)).thenReturn(List.of(projection(total)));
    }

    private static PuzzleCountProjection projection(long total) {
        return new PuzzleCountProjection() {
            public Long getScheduleId() { return SCHEDULE_ID; }
            public long getPuzzleCount() { return total; }
            public long getCompletedPuzzleCount() { return 0; }
        };
    }

    @Test
    @DisplayName("첫 작업 완료: 퍼즐이 없으면 자동 생성하고 조각을 지급한다")
    void awardOnComplete_createsPuzzleAndPiece() throws Exception {
        when(puzzleRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.empty());
        when(puzzleRepository.save(any())).thenAnswer(inv -> {
            Puzzle saved = inv.getArgument(0);
            setId(saved, PUZZLE_ID);
            return saved;
        });
        when(puzzlePieceRepository.findByScheduleItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(puzzlePieceRepository.nextPosition(PUZZLE_ID)).thenReturn(0);
        when(puzzlePieceRepository.save(any())).thenAnswer(inv -> {
            PuzzlePiece saved = inv.getArgument(0);
            setId(saved, 11L);
            return saved;
        });
        stubCounts(1, 3);

        PuzzlePieceAwarder.AwardResult result = service.awardOnComplete(item);

        assertThat(result.awarded()).isTrue();
        assertThat(result.puzzlePieceId()).isEqualTo(11L);
        verify(puzzleRepository).save(any());
    }

    @Test
    @DisplayName("같은 작업을 다시 완료해도 조각을 중복 지급하지 않는다 (핵심 정책 4)")
    void awardOnComplete_doesNotAwardTwice() throws Exception {
        Puzzle puzzle = puzzle();
        PuzzlePiece existing = PuzzlePiece.builder().puzzleId(PUZZLE_ID).scheduleItemId(ITEM_ID).position(0)
                .earnedAt(java.time.LocalDateTime.of(2026, 8, 19, 10, 0)).build();
        setId(existing, 11L);
        when(puzzleRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(puzzle));
        when(puzzlePieceRepository.findByScheduleItemId(ITEM_ID)).thenReturn(Optional.of(existing));
        stubCounts(1, 3);

        PuzzlePieceAwarder.AwardResult result = service.awardOnComplete(item);

        assertThat(result.awarded()).isFalse();
        assertThat(result.puzzlePieceId()).isEqualTo(11L);
        verify(puzzlePieceRepository, never()).save(any());
    }

    @Test
    @DisplayName("유효한 작업을 모두 완료하면 퍼즐이 COMPLETED 가 된다")
    void awardOnComplete_completesPuzzle() throws Exception {
        Puzzle puzzle = puzzle();
        when(puzzleRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(puzzle));
        when(puzzlePieceRepository.findByScheduleItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(puzzlePieceRepository.nextPosition(PUZZLE_ID)).thenReturn(2);
        when(puzzlePieceRepository.save(any())).thenAnswer(inv -> {
            PuzzlePiece saved = inv.getArgument(0);
            setId(saved, 13L);
            return saved;
        });
        stubCounts(3, 3);

        service.awardOnComplete(item);

        assertThat(puzzle.getStatus()).isEqualTo(PuzzleStatus.COMPLETED);
        assertThat(puzzle.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("작업이 추가돼 칸이 늘면 퍼즐이 다시 IN_PROGRESS 로 돌아간다")
    void refreshOnItemsChanged_revertsToInProgress() throws Exception {
        Puzzle puzzle = puzzle();
        puzzle.refreshCompletion(3, 3, java.time.LocalDateTime.of(2026, 8, 19, 12, 0));
        assertThat(puzzle.getStatus()).isEqualTo(PuzzleStatus.COMPLETED);
        when(puzzleRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(puzzle));
        stubCounts(3, 4);

        service.refreshOnItemsChanged(schedule);

        assertThat(puzzle.getStatus()).isEqualTo(PuzzleStatus.IN_PROGRESS);
        assertThat(puzzle.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("퍼즐이 아직 없으면 갱신 요청은 아무것도 하지 않는다")
    void refreshOnItemsChanged_noPuzzle_doesNothing() {
        when(puzzleRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.empty());

        service.refreshOnItemsChanged(schedule);

        verify(puzzlePieceRepository, never()).countValidByPuzzleId(anyLong(), eq(ScheduleItemStatus.CANCELLED));
    }
}
