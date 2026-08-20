package hackathon.app.domain.puzzle.service;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.policy.PuzzlePieceAwarder;
import hackathon.app.domain.scheduleitem.repository.PuzzleCountProjection;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.image.service.ImageService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작업 완료 → 퍼즐 조각 지급 (6번 작업 API 와 7번 퍼즐 도메인의 연결점).
 *
 * 규칙
 * - 스케줄 1개 = 퍼즐 1개. 스케줄의 작업을 처음 완료할 때 퍼즐을 자동 생성한다 (제목 = 스케줄 제목, 기본 공개).
 * - 작업 1건 = 조각 1개. puzzle_pieces.schedule_item_id UNIQUE 로 중복 지급을 막는다 (핵심 정책 4).
 * - position 은 획득한 순서(0부터).
 * - 유효한 작업(삭제·CANCELLED 제외)을 모두 완료하면 퍼즐이 COMPLETED 가 된다.
 *   완성 후 작업이 추가되면 다시 IN_PROGRESS 로 돌아간다 (작업 추가·삭제·취소 시 refreshOnItemsChanged 호출).
 */
@Service
@RequiredArgsConstructor
public class PuzzlePieceAwardService implements PuzzlePieceAwarder {

    private final PuzzleRepository puzzleRepository;
    private final PuzzlePieceRepository puzzlePieceRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ImageService imageService;
    private final Clock clock;

    @Override
    @Transactional
    public AwardResult awardOnComplete(ScheduleItem item) {
        Schedule schedule = item.getSchedule();
        if (schedule == null) return AwardResult.none();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        Puzzle puzzle = puzzleRepository.findByScheduleId(schedule.getId())
                .orElseGet(() -> puzzleRepository.save(Puzzle.builder()
                        .scheduleId(schedule.getId())
                        .userId(schedule.getUserId())
                        .imageId(imageService.findRandomImageIdByCategoryId(schedule.getCategoryId()).orElse(null))
                        .title(schedule.getTitle())
                        .build()));

        // 이미 이 작업으로 조각을 받았으면 새로 지급하지 않는다 (완료 → 다른 상태 → 다시 완료 해도 1개)
        PuzzlePiece piece = puzzlePieceRepository.findByScheduleItemId(item.getId()).orElse(null);
        boolean awarded = false;
        if (piece == null) {
            // 동시에 같은 작업을 완료하는 극단적인 경우에는 puzzle_pieces.schedule_item_id UNIQUE 제약이
            // 최종 방어선이 된다 (조각을 두 번 주는 대신 그 요청이 실패한다).
            piece = puzzlePieceRepository.save(PuzzlePiece.builder()
                    .puzzleId(puzzle.getId())
                    .scheduleItemId(item.getId())
                    .position(puzzlePieceRepository.nextPosition(puzzle.getId()))
                    .earnedAt(now)
                    .build());
            awarded = true;
        }

        refreshPuzzleCompletion(puzzle, schedule.getId(), now);

        return new AwardResult(awarded, piece.getId());
    }

    @Override
    @Transactional
    public void refreshOnItemsChanged(Schedule schedule) {
        puzzleRepository.findByScheduleId(schedule.getId()).ifPresent(puzzle ->
                refreshPuzzleCompletion(puzzle, schedule.getId(),
                        LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)));
    }

    /** 조각 수와 스케줄의 유효한 작업 수를 비교해 완성 여부를 갱신한다 */
    private void refreshPuzzleCompletion(Puzzle puzzle, Long scheduleId, LocalDateTime now) {
        long earned = puzzlePieceRepository.countValidByPuzzleId(puzzle.getId(), ScheduleItemStatus.CANCELLED);
        List<PuzzleCountProjection> counts = scheduleItemRepository.countPuzzlesByScheduleIds(
                List.of(scheduleId), ScheduleItemStatus.COMPLETED, ScheduleItemStatus.CANCELLED);
        long total = counts.isEmpty() ? 0 : counts.getFirst().getPuzzleCount();
        puzzle.refreshCompletion(earned, total, now);
    }
}
