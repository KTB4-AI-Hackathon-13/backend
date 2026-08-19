package hackathon.app.domain.puzzle.dto;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.global.common.TimeUtils;
import java.time.OffsetDateTime;

/** 퍼즐 목록 항목 — 진행률(획득 조각/전체 조각) 포함 */
public record PuzzleSummaryResponse(
        Long id,
        Long scheduleId,
        String title,
        PuzzleStatus status,
        PuzzleVisibility visibility,
        Long imageId,
        long pieceCount,
        long earnedPieceCount,
        OffsetDateTime completedAt
) {
    public static PuzzleSummaryResponse of(Puzzle puzzle, long pieceCount, long earnedPieceCount) {
        return new PuzzleSummaryResponse(
                puzzle.getId(),
                puzzle.getScheduleId(),
                puzzle.getTitle(),
                puzzle.getStatus(),
                puzzle.getVisibility(),
                puzzle.getImageId(),
                pieceCount,
                earnedPieceCount,
                TimeUtils.toOffset(puzzle.getCompletedAt())
        );
    }
}
