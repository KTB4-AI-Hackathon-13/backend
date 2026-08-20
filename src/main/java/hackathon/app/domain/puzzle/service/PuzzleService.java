package hackathon.app.domain.puzzle.service;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.dto.PuzzleDetailResponse;
import hackathon.app.domain.puzzle.dto.PuzzleSummaryResponse;
import hackathon.app.domain.puzzle.dto.GalleryPuzzleDetailResponse;
import hackathon.app.domain.puzzle.dto.GalleryPuzzleResponse;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.puzzle.repository.PuzzleLikeRepository;
import hackathon.app.domain.puzzle.repository.PuzzleSpecs;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.PuzzleCountProjection;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.global.common.CursorCodec;
import hackathon.app.global.common.CursorPage;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import hackathon.app.user.domain.User;
import hackathon.app.user.infrastructure.JpaUserRepository;
import hackathon.app.preference.infrastructure.JpaUserPreferenceRepository;

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
    private final PuzzleLikeRepository puzzleLikeRepository;
    private final JpaUserRepository userRepository;
    private final JpaUserPreferenceRepository preferenceRepository;
    private final Clock clock;

    /** GET /puzzles/mine */
    public CursorPage<PuzzleSummaryResponse> getMyPuzzles(Long userId, PuzzleStatus status,
                                                          Integer size, String cursor) {
        return findPuzzles(Specification.allOf(
                PuzzleSpecs.ownedBy(userId),
                PuzzleSpecs.hasStatus(status),
                PuzzleSpecs.idLessThan(CursorCodec.decode(cursor))), size);
    }

    /** GET /users/{userId}/public-puzzles — 공개·완성 퍼즐만. */
    public CursorPage<PuzzleSummaryResponse> getPublicPuzzles(Long ownerUserId, String sort,
                                                              Integer size, String cursor) {
        // 사용자 프로필은 명세상 카드 목록만 제공하며 상세 이동 권한을 부여하지 않는다.
        return findPuzzles(Specification.allOf(
                PuzzleSpecs.ownedBy(ownerUserId),
                PuzzleSpecs.hasStatus(PuzzleStatus.COMPLETED),
                PuzzleSpecs.isPublic(),
                PuzzleSpecs.idLessThan(CursorCodec.decode(cursor))), size);
    }

    public CursorPage<PuzzleSummaryResponse> getPublicPuzzles(Long ownerUserId, Integer size, String cursor) {
        return getPublicPuzzles(ownerUserId, "LATEST", size, cursor);
    }

    /**
     * GET /puzzles/{puzzleId} — 퍼즐 정보 + 조각별 획득 상태.
     * 내 퍼즐 상세 API는 소유자 전용이다. 공개 작품 상세는 /gallery/puzzles/{id}를 사용한다.
     */
    public PuzzleDetailResponse getPuzzle(Long viewerUserId, Long puzzleId) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new ApiException(ErrorCode.PUZZLE_NOT_FOUND));
        if (!puzzle.isOwnedBy(viewerUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        List<ScheduleItem> items = scheduleItemRepository
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(puzzle.getScheduleId())
                .stream()
                .filter(ScheduleItem::countsAsPuzzlePiece)
                .toList();
        List<PuzzlePiece> pieces = puzzlePieceRepository.findByPuzzleIdOrderByPositionAsc(puzzle.getId());

        return PuzzleDetailResponse.of(puzzle, items, pieces);
    }

    @Transactional
    public PuzzleSummaryResponse changeVisibility(Long userId, Long puzzleId, PuzzleVisibility visibility) {
        Puzzle puzzle = ownedPuzzle(userId, puzzleId);
        if (visibility == PuzzleVisibility.PUBLIC && !puzzle.isCompleted()) {
            throw new ApiException(ErrorCode.PUZZLE_NOT_COMPLETED);
        }
        puzzle.changeVisibility(visibility);
        long total = countTotalPieces(List.of(puzzle)).getOrDefault(puzzle.getScheduleId(), 0L);
        long earned = countEarnedPieces(List.of(puzzle)).getOrDefault(puzzle.getId(), 0L);
        return PuzzleSummaryResponse.of(puzzle, total, earned);
    }

    public CursorPage<GalleryPuzzleResponse> getGallery(Long viewerUserId, String sort,
                                                        Integer size, String cursor) {
        int pageSize = normalizeSize(size);
        List<Puzzle> publicPuzzles = new ArrayList<>(puzzleRepository.findAll(Specification.allOf(
                PuzzleSpecs.hasStatus(PuzzleStatus.COMPLETED), PuzzleSpecs.isPublic())));
        Map<Long, Long> likeCounts = countLikes(publicPuzzles);
        Comparator<Puzzle> order = "POPULAR".equalsIgnoreCase(sort)
                ? Comparator.<Puzzle>comparingLong(p -> likeCounts.getOrDefault(p.getId(), 0L)).reversed()
                        .thenComparing(Puzzle::getId, Comparator.reverseOrder())
                : Comparator.comparing(Puzzle::getCompletedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Puzzle::getId, Comparator.reverseOrder());
        publicPuzzles.sort(order);
        Long cursorId = CursorCodec.decode(cursor);
        int start = indexAfter(publicPuzzles, cursorId);
        int end = Math.min(start + pageSize, publicPuzzles.size());
        List<Puzzle> page = publicPuzzles.subList(start, end);
        Map<Long, User> users = userRepository.findAllById(page.stream().map(Puzzle::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<GalleryPuzzleResponse> items = page.stream().map(puzzle -> GalleryPuzzleResponse.of(
                puzzle, publicNickname(users.get(puzzle.getUserId())),
                likeCounts.getOrDefault(puzzle.getId(), 0L),
                viewerUserId != null && puzzleLikeRepository
                        .existsByIdPuzzleIdAndIdUserId(puzzle.getId(), viewerUserId))).toList();
        boolean hasNext = end < publicPuzzles.size();
        String nextCursor = page.isEmpty() ? null : CursorCodec.encode(page.getLast().getId());
        return CursorPage.of(items, nextCursor, hasNext);
    }

    public GalleryPuzzleDetailResponse getGalleryPuzzle(Long viewerUserId, Long puzzleId) {
        Puzzle puzzle = publicCompletedPuzzle(puzzleId);
        User author = userRepository.findById(puzzle.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        List<ScheduleItem> items = scheduleItemRepository
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(puzzle.getScheduleId()).stream()
                .filter(ScheduleItem::countsAsPuzzlePiece).toList();
        PuzzleDetailResponse detail = PuzzleDetailResponse.of(puzzle, items,
                puzzlePieceRepository.findByPuzzleIdOrderByPositionAsc(puzzleId));
        return new GalleryPuzzleDetailResponse(detail, author.getId(), publicNickname(author),
                puzzleLikeRepository.countByIdPuzzleId(puzzleId),
                viewerUserId != null && puzzleLikeRepository.existsByIdPuzzleIdAndIdUserId(puzzleId, viewerUserId));
    }

    @Transactional
    public GalleryPuzzleResponse like(Long userId, Long puzzleId) {
        Puzzle puzzle = publicCompletedPuzzle(puzzleId);
        if (!puzzleLikeRepository.existsByIdPuzzleIdAndIdUserId(puzzleId, userId)) {
            puzzleLikeRepository.save(hackathon.app.domain.puzzle.entity.PuzzleLike.create(
                    puzzleId, userId, LocalDateTime.now(clock)));
        }
        User author = userRepository.findById(puzzle.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return GalleryPuzzleResponse.of(puzzle, publicNickname(author),
                puzzleLikeRepository.countByIdPuzzleId(puzzleId), true);
    }

    @Transactional
    public void unlike(Long userId, Long puzzleId) {
        publicCompletedPuzzle(puzzleId);
        puzzleLikeRepository.deleteByIdPuzzleIdAndIdUserId(puzzleId, userId);
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

    private Puzzle ownedPuzzle(Long userId, Long puzzleId) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new ApiException(ErrorCode.PUZZLE_NOT_FOUND));
        if (!puzzle.isOwnedBy(userId)) throw new ApiException(ErrorCode.FORBIDDEN);
        return puzzle;
    }

    private Puzzle publicCompletedPuzzle(Long puzzleId) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new ApiException(ErrorCode.PUZZLE_NOT_FOUND));
        if (!puzzle.isCompleted() || puzzle.getVisibility() != PuzzleVisibility.PUBLIC) {
            throw new ApiException(ErrorCode.PUZZLE_NOT_PUBLIC);
        }
        return puzzle;
    }

    private Map<Long, Long> countLikes(List<Puzzle> puzzles) {
        if (puzzles.isEmpty()) return Map.of();
        return puzzleLikeRepository.countByPuzzleIds(puzzles.stream().map(Puzzle::getId).toList()).stream()
                .collect(Collectors.toMap(PuzzleLikeRepository.LikeCountProjection::getPuzzleId,
                        PuzzleLikeRepository.LikeCountProjection::getLikeCount));
    }

    private int indexAfter(List<Puzzle> puzzles, Long cursorId) {
        if (cursorId == null) return 0;
        for (int i = 0; i < puzzles.size(); i++) {
            if (puzzles.get(i).getId().equals(cursorId)) return i + 1;
        }
        throw new ApiException(ErrorCode.INVALID_CURSOR);
    }

    private String publicNickname(User user) {
        if (user == null) return "알 수 없음";
        return preferenceRepository.findById(user.getId())
                .filter(hackathon.app.preference.domain.UserPreference::isGalleryNicknameVisible)
                .map(ignored -> user.getNickname())
                .orElse("익명");
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
