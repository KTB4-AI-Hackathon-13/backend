package hackathon.app.domain.puzzle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.dto.PuzzleDetailResponse;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PuzzleServiceTest {

    private static final Long OWNER = 1L;
    private static final Long OTHER = 2L;
    private static final Long SCHEDULE_ID = 104L;
    private static final Long PUZZLE_ID = 7L;

    @Mock PuzzleRepository puzzleRepository;
    @Mock PuzzlePieceRepository puzzlePieceRepository;
    @Mock ScheduleItemRepository scheduleItemRepository;

    @InjectMocks PuzzleService service;

    private Schedule schedule;

    @BeforeEach
    void setUp() throws Exception {
        schedule = Schedule.builder().userId(OWNER).title("운동 루틴").status(ScheduleStatus.ACTIVE)
                .source(ChangeSource.AI).startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 8, 31))
                .build();
        setId(schedule, SCHEDULE_ID);
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    private Puzzle puzzle(boolean completed) throws Exception {
        return puzzle(completed, PuzzleVisibility.PUBLIC);
    }

    private Puzzle puzzle(boolean completed, PuzzleVisibility visibility) throws Exception {
        Puzzle p = Puzzle.builder().scheduleId(SCHEDULE_ID).userId(OWNER).title("운동 루틴")
                .visibility(visibility).build();
        setId(p, PUZZLE_ID);
        if (completed) {
            p.refreshCompletion(2, 2, LocalDateTime.of(2026, 8, 19, 12, 0));
        }
        return p;
    }

    private ScheduleItem item(Long id, String title) throws Exception {
        ScheduleItem i = ScheduleItem.builder().schedule(schedule).title(title)
                .scheduledDate(LocalDate.of(2026, 8, 19)).build();
        setId(i, id);
        return i;
    }

    @Test
    @DisplayName("상세: 유효한 작업 전부가 칸이 되고, 조각 못 받은 칸은 earned=false")
    void getPuzzle_showsEveryPieceSlot() throws Exception {
        ScheduleItem done = item(1301L, "하체 운동");
        ScheduleItem todo = item(1302L, "상체 운동");
        PuzzlePiece piece = PuzzlePiece.builder().puzzleId(PUZZLE_ID).scheduleItemId(1301L).position(0)
                .earnedAt(LocalDateTime.of(2026, 8, 19, 10, 0)).build();
        setId(piece, 11L);
        when(puzzleRepository.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle(false)));
        when(scheduleItemRepository.findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(SCHEDULE_ID))
                .thenReturn(List.of(done, todo));
        when(puzzlePieceRepository.findByPuzzleIdOrderByPositionAsc(PUZZLE_ID)).thenReturn(List.of(piece));

        PuzzleDetailResponse res = service.getPuzzle(OWNER, PUZZLE_ID);

        assertThat(res.pieceCount()).isEqualTo(2);
        assertThat(res.earnedPieceCount()).isEqualTo(1);
        assertThat(res.pieces()).extracting(PuzzleDetailResponse.Piece::earned).containsExactly(true, false);
        assertThat(res.pieces().getFirst().position()).isZero();
        assertThat(res.pieces().getLast().position()).isNull();
    }

    @Test
    @DisplayName("상세: CANCELLED 작업은 칸에서 빠진다")
    void getPuzzle_excludesCancelledItems() throws Exception {
        ScheduleItem valid = item(1301L, "하체 운동");
        ScheduleItem cancelled = item(1302L, "취소된 운동");
        cancelled.changeStatus(ScheduleItemStatus.CANCELLED, LocalDateTime.of(2026, 8, 19, 10, 0));
        when(puzzleRepository.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle(false)));
        when(scheduleItemRepository.findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(SCHEDULE_ID))
                .thenReturn(List.of(valid, cancelled));
        when(puzzlePieceRepository.findByPuzzleIdOrderByPositionAsc(PUZZLE_ID)).thenReturn(List.of());

        PuzzleDetailResponse res = service.getPuzzle(OWNER, PUZZLE_ID);

        assertThat(res.pieceCount()).isEqualTo(1);
        assertThat(res.pieces()).extracting(PuzzleDetailResponse.Piece::scheduleItemId).containsExactly(1301L);
    }

    @Test
    @DisplayName("상세: 없는 퍼즐이면 404 PUZZLE_NOT_FOUND")
    void getPuzzle_notFound() {
        when(puzzleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPuzzle(OWNER, 999L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.PUZZLE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세: 타인의 미완성 퍼즐은 403 PUZZLE_NOT_PUBLIC")
    void getPuzzle_othersIncomplete_forbidden() throws Exception {
        when(puzzleRepository.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle(false)));

        assertThatThrownBy(() -> service.getPuzzle(OTHER, PUZZLE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.PUZZLE_NOT_PUBLIC);
    }

    @Test
    @DisplayName("상세: 타인이라도 공개·완성 퍼즐은 조회할 수 있다")
    void getPuzzle_othersCompletedPublic_allowed() throws Exception {
        when(puzzleRepository.findById(PUZZLE_ID)).thenReturn(Optional.of(puzzle(true)));
        when(scheduleItemRepository.findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(SCHEDULE_ID))
                .thenReturn(List.of(item(1301L, "하체 운동")));
        when(puzzlePieceRepository.findByPuzzleIdOrderByPositionAsc(PUZZLE_ID)).thenReturn(List.of());

        PuzzleDetailResponse res = service.getPuzzle(OTHER, PUZZLE_ID);

        assertThat(res.id()).isEqualTo(PUZZLE_ID);
    }

    @Test
    @DisplayName("상세: 타인의 비공개 완성 퍼즐은 조회할 수 없다")
    void getPuzzle_othersCompletedPrivate_forbidden() throws Exception {
        when(puzzleRepository.findById(PUZZLE_ID))
                .thenReturn(Optional.of(puzzle(true, PuzzleVisibility.PRIVATE)));

        assertThatThrownBy(() -> service.getPuzzle(OTHER, PUZZLE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.PUZZLE_NOT_PUBLIC);
    }
}
