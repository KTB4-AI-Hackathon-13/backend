package hackathon.app.domain.puzzle.service;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.dto.PuzzleDetailResponse;
import hackathon.app.domain.puzzle.dto.PuzzleSummaryResponse;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.puzzle.repository.PuzzleSpecs;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.PuzzleCountProjection;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.global.common.CursorCodec;
import hackathon.app.global.common.CursorPage;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 7. 퍼즐 API (MVP) — 내 퍼즐 목록·상세, 사용자 공개 퍼즐 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PuzzleService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final PuzzleRepository puzzleRepository;
    private final PuzzlePieceRepository puzzlePieceRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    /** GET /puzzles/mine */
    public CursorPage<PuzzleSummaryResponse> getMyPuzzles(Long userId, PuzzleStatus status,
                                                          Integer size, String cursor) {
        return findPuzzles(Specification.allOf(
                PuzzleSpecs.ownedBy(userId),
                PuzzleSpecs.hasStatus(status),
                PuzzleSpecs.idLessThan(CursorCodec.decode(cursor))), size);
    }

    /**
     * GET /users/{userId}/public-puzzles — 완성(COMPLETED) 퍼즐만.
     * 기존 API 경로는 유지하지만 공개/비공개 값은 조회 조건으로 사용하지 않는다.
     */
    public CursorPage<PuzzleSummaryResponse> getPublicPuzzles(Long ownerUserId, Integer size, String cursor) {
        return findPuzzles(Specification.allOf(
                PuzzleSpecs.ownedBy(ownerUserId),
                PuzzleSpecs.hasStatus(PuzzleStatus.COMPLETED),
                PuzzleSpecs.idLessThan(CursorCodec.decode(cursor))), size);
    }

    /**
     * GET /puzzles/{puzzleId} — 퍼즐 정보 + 조각별 획득 상태.
     * 본인 퍼즐이면 항상 조회 가능. 타인 퍼즐은 완성된 것만 조회할 수 있다.
     */
    public PuzzleDetailResponse getPuzzle(Long viewerUserId, Long puzzleId) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new ApiException(ErrorCode.PUZZLE_NOT_FOUND));
        if (!puzzle.isOwnedBy(viewerUserId) && !puzzle.isCompleted()) {
            throw new ApiException(ErrorCode.PUZZLE_NOT_PUBLIC);
        }

        List<ScheduleItem> items = scheduleItemRepository
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(puzzle.getScheduleId())
                .stream()
                .filter(ScheduleItem::countsAsPuzzlePiece)
                .toList();
        List<PuzzlePiece> pieces = puzzlePieceRepository.findByPuzzleIdOrderByPositionAsc(puzzle.getId());

        return PuzzleDetailResponse.of(puzzle, items, pieces);
    }

    // ===== 내부 헬퍼 =====

    private CursorPage<PuzzleSummaryResponse> findPuzzles(Specification<Puzzle> spec, Integer size) {
        int pageSize = normalizeSize(size);

        // size + 1 건을 조회해서 다음 페이지 존재 여부를 판단한다.
        List<Puzzle> rows = puzzleRepository.findAll(spec,
                PageRequest.of(0, pageSize + 1, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        boolean hasNext = rows.size() > pageSize;
        List<Puzzle> puzzles = hasNext ? rows.subList(0, pageSize) : rows;

        Map<Long, Long> earnedCounts = countEarnedPieces(puzzles);
        Map<Long, Long> totalCounts = countTotalPieces(puzzles);

        List<PuzzleSummaryResponse> items = puzzles.stream()
                .map(p -> PuzzleSummaryResponse.of(p,
                        totalCounts.getOrDefault(p.getScheduleId(), 0L),
                        earnedCounts.getOrDefault(p.getId(), 0L)))
                .toList();

        String nextCursor = puzzles.isEmpty() ? null : CursorCodec.encode(puzzles.getLast().getId());
        return CursorPage.of(items, nextCursor, hasNext);
    }

    /** 퍼즐별 획득 조각 수 */
    private Map<Long, Long> countEarnedPieces(List<Puzzle> puzzles) {
        if (puzzles.isEmpty()) {
            return Map.of();
        }
        return puzzlePieceRepository.countValidByPuzzleIds(puzzles.stream().map(Puzzle::getId).toList(),
                        ScheduleItemStatus.CANCELLED).stream()
                .collect(Collectors.toMap(PuzzlePieceRepository.PieceCountProjection::getPuzzleId,
                        PuzzlePieceRepository.PieceCountProjection::getPieceCount));
    }

    /** 스케줄별 전체 조각 수(= 유효한 작업 수). 5번 퍼즐 수 계산과 같은 기준 */
    private Map<Long, Long> countTotalPieces(List<Puzzle> puzzles) {
        if (puzzles.isEmpty()) {
            return Map.of();
        }
        List<Long> scheduleIds = puzzles.stream().map(Puzzle::getScheduleId).toList();
        return scheduleItemRepository.countPuzzlesByScheduleIds(scheduleIds,
                        ScheduleItemStatus.COMPLETED, ScheduleItemStatus.CANCELLED).stream()
                .collect(Collectors.toMap(PuzzleCountProjection::getScheduleId,
                        PuzzleCountProjection::getPuzzleCount, (a, b) -> a));
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
