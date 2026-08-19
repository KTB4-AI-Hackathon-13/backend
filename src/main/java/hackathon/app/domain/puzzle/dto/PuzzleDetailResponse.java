package hackathon.app.domain.puzzle.dto;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.global.common.TimeUtils;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 퍼즐 상세 — 퍼즐 정보 + 조각별 획득 상태.
 * pieces[] 는 스케줄의 유효한 작업 전부(= 퍼즐 칸 전부)를 담고, 아직 못 받은 칸은 earned=false.
 * 삭제·취소된 작업의 조각은 칸이 사라지므로 진행률에서 함께 빠진다.
 */
public record PuzzleDetailResponse(
        Long id,
        Long scheduleId,
        String title,
        PuzzleStatus status,
        PuzzleVisibility visibility,
        Long imageId,
        long pieceCount,
        long earnedPieceCount,
        OffsetDateTime completedAt,
        List<Piece> pieces
) {
    /** position 은 획득한 순서(0부터). 아직 못 받았으면 null */
    public record Piece(
            Long pieceId,
            Long scheduleItemId,
            String scheduleItemTitle,
            LocalDate scheduledDate,
            boolean earned,
            Integer position,
            OffsetDateTime earnedAt
    ) {
    }

    public static PuzzleDetailResponse of(Puzzle puzzle, List<ScheduleItem> items, List<PuzzlePiece> pieces) {
        Map<Long, PuzzlePiece> byItemId = pieces.stream()
                .collect(java.util.stream.Collectors.toMap(PuzzlePiece::getScheduleItemId, p -> p));

        List<Piece> pieceViews = items.stream()
                .map(item -> {
                    PuzzlePiece p = byItemId.get(item.getId());
                    return new Piece(
                            p == null ? null : p.getId(),
                            item.getId(),
                            item.getTitle(),
                            item.getScheduledDate(),
                            p != null,
                            p == null ? null : p.getPosition(),
                            p == null ? null : TimeUtils.toOffset(p.getEarnedAt()));
                })
                .toList();

        long earned = pieceViews.stream().filter(Piece::earned).count();

        return new PuzzleDetailResponse(
                puzzle.getId(),
                puzzle.getScheduleId(),
                puzzle.getTitle(),
                puzzle.getStatus(),
                puzzle.getVisibility(),
                puzzle.getImageId(),
                items.size(),
                earned,
                TimeUtils.toOffset(puzzle.getCompletedAt()),
                pieceViews
        );
    }
}
